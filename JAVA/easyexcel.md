### excel导出实现

> 异步导出就是客户端发送导出请求之后，后台接受到导出请求直接返回，而不是阻塞式的等待响应结果
> 

### 1.为什么使用异步导出？

同步导出在大批量数据导出时经常会发生服务器端oom或者客户端连接超时的情况，同步导出是希望客户端同步阻塞直到导出结束，如果数据量大就会出现等待时间较长的情况，所以在数据量大的时候，为了客户端更好的体验，进行异步导出。

### 2. 异步导出的方案有哪些？

目前采用的方案是客户端发送导出请求，然后预测服务进行一个异步任务，异步任务进行导出，先导出到本地excel，然后从本地excel中获取数据上传到S3服务器，这样生成excel的部分和上传到服务器是分离的，不会一直访问文件服务器连接，而且保证了excel的完整性，即使服务done掉，excel文件还在。

[https://www.processon.com/diagraming/64c74ae5470d721c4e3963cb](https://www.notion.so/https-www-processon-com-diagraming-64c74ae5470d721c4e3963cb-6b3d5cc3808a4bc2b4293850651f90f0?pvs=21) 

1. 首先分批次读取数据
2. 将批次数据写入到本地文件
3. 写入完成之后读取为字节流，生成MockMultipartFile web文件对象
4. 通过文件服务器client对象上传文件

### 3.异步导出关键步骤代码

1. 首先获取一个ExcelWriter，如果是同步直接写入到response的输出字节流，异步写入到本地文件夹路径

```java
/**
     * 获取一个写入response的ExcelWriter
     *
     * @param fileName
     * @return
     * @throws Exception
     */public static ExcelWriterBuilder writeResponse(String fileName) throws Exception {
// 获取HttpServletResponse对象HttpServletResponse response = getResponse();
// 设置响应头//        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
// 这里URLEncoder.encode可以防止中文乱码 当然和easyexcel没有关系
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xls");
//这里需要设置不关闭流return EasyExcel.write(response.getOutputStream());
    }

/**
     * 获取当前上下文的response
     *
     * @return
     */public static HttpServletResponse getResponse() {
        return ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getResponse();
    }

/**
     * 获取文件名
     *
     * @param namePrefix 文件名前缀
     * @param isNetWork  是否是网络传输
     * @return
     * @throws UnsupportedEncodingException
     */@SneakyThrows
    public static String getFileName(String namePrefix, boolean isNetWork) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String nowTime = sdf.format(new Date());
//这里是为了网络传输乱码问题if (isNetWork) {
            return URLEncoder.encode(namePrefix + "-" + nowTime, "UTF-8").replaceAll("\\+", "%20");
        }
        return namePrefix + nowTime + ".xls";
    }

/**
     * 获取一个本地的ExcelWriter
     *
     * @param filePath
     * @return
     */public static ExcelWriter writeLocal(String filePath) {
        return EasyExcelFactory.write(filePath).build();
    }
```

1. 然后执行write方法，**将数据写入到内存缓冲区中，注意此时并没有写入到输出流**

```java
//这里注意head和data都是嵌套list，head外层list控制列，里面控制行数据，data就是多行WriteSheet writeSheet = EasyExcel.writerSheet(sheetName).head(excelHeadOrDataHolder.heads).build();
excelWriter.write(excelHeadOrDataHolder.data, writeSheet1);
```

1. 如果想要一些样式，那么需要在构建excelWriter的时候注册handle
2. 最后调用finish方法，**此时会将内存缓冲区中的数据写入到输出流中，然后关闭输出流**，到这里excel才到处结束

```java
/**
     * 写入
     */public void finish() {
        completableFuture.join();
        if (excelWriter != null) {
            EXCEL_EXPORT_INFO.info("本次excel导出任务完成=========================关闭excelWriter=========================");
            excelWriter.finish();
        }
        if (isUpload) {
            EXCEL_EXPORT_INFO.info("本次excel导出任务完成=========================上传到s3=========================");
            this.fileStoreDTO = uploadS3();
        }
    }
```

1. 上传文件服务器
    1. 读取文件为输入流
    2. 删除本地文件
    3. 将输入流写入到输出流，转换成字节数组
    4. 构建MockMultipartFile 对象
    5. 调用clien对象上传到服务器，如果使用对应的sdk，此时会返回文件url
    6. 最后关闭流
    
    ```java
    /**
         * 方法<code>uploadToS3</code>说明: 读取本地文件 上传至S3 并删除本地文件
         *
         * @param filePath 文件在服务器的目录
         * @return com.yt.bi.common.filestore.domain.dto.FileStoreDTO
         * @author renqige
         * @since 2023/7/3
         */public FileStoreDTO localFileUploadToS3(String filePath, String fileName) throws IOException {
            FileInputStream fileInputStream = null;
            ByteArrayOutputStream byteArrayOutputStream = null;
            ByteArrayInputStream byteArrayInputStream = null;
    
            try {
    // 创建文件输入流
                fileInputStream = new FileInputStream(filePath);
    
    // 删除服务器的文件// 创建File对象File file = new File(filePath);
    
    // 检查文件是否存在if (file.exists()) {
    // 删除文件
                file.delete();
                }
    
    // 创建字节数组输出流
                byteArrayOutputStream = new ByteArrayOutputStream();
    
    // 读取文件流并写入字节数组输出流byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
    
    // 将字节数组输出流转换为字节数组byte[] byteArray = byteArrayOutputStream.toByteArray();
    
    // 创建ByteArrayInputStream
                byteArrayInputStream = new ByteArrayInputStream(byteArray);
    
                String[] split = fileName.split("\\.");
    
                MultipartFile multipartFile = new MockMultipartFile(split[1], fileName, "." + split[1], byteArrayInputStream);
                List<FileStoreDTO> aFalse = fileStoreService.uploadFiles(new MultipartFile[]{multipartFile}, "bi-operatio", "N");
    
                return aFalse.get(0);
            } finally {
                fileInputStream.close();
                byteArrayOutputStream.close();
                byteArrayInputStream.close();
            }
        }
    
    ```
    
2. 获取到文件url之后可以选择放入数据库还是发送钉钉消息。

### 4. 成功时返回json，异常时返回excel

当某一时刻不需要返回excel时，在同步的情况下，需要将response重置，然后设置head为json格式

```java
//在这里将response设置为返回jsonpublic static void convertResponseToJson() {
    HttpServletResponse response = getResponse();
    response.reset();
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
}

```

### 5.导出动态表格，例如map中的key为表头

导出动态表头easyexcel是不支持实体导出的，所以需要使用原始的嵌套list进行，所以只需要将原始数据转为嵌套list就好了

```java
//这里注意实体需要使用别名的话，需要使用hutool的注解，或者一些映射关系将实体的字段名称转为excel的表头名称，下面代码只是提供一种思路，方法又很多种//最后表头转换的格式大致为，bean 字段 name -> list<list<name>>  里面的list是列数据，外面list是行数据public static List<List<String>> buildFromObj(Collection<?> items, HeadOrDataEnum headOrDataEnum) {
    if (CollUtil.isEmpty(items)) {
        return CollUtil.newArrayList();
    }
    Stream<Map<String, Object>> mapStream = items.stream()
            .map(ExcelUtil::beanFlatMap);

    if (Objects.equals(HeadOrDataEnum.HEAD, headOrDataEnum)) {
        return mapStream
                .limit(1)
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .map(CollUtil::newArrayList)
                .collect(Collectors.toList());
    }
    if (Objects.equals(HeadOrDataEnum.DATA, headOrDataEnum)) {
        return mapStream
                .map(Map::values)
                .map(it -> it.stream().map(Object::toString).collect(Collectors.toList()))
                .collect(Collectors.toList());
    }
    return Collections.emptyList();
}

```