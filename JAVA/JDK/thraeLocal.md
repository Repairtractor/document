### threadLocal源码

### threadLocal理解

1. 局部变量：作用域只能在方法内，当方法栈帧出栈，生命周期结束
2. 实例变量：作用域在一个类内部，所有方法共有，实例被gc，生命周期结束
3. 静态变量：作用域在整个项目中，范围最大的变量，生命周期与类相同，类被gc才会被销毁
4. 在单线程的情况下，只需要前三个中作用域变量就可以了，但是当多线程又希望范围较大时，静态变量会有并发问题，这个时候就需要threadLocal了，每个线程都有一个自己的本地变量，并且可以在多个类之间访问
5. `ThreadLocal.withInitial(() -> 5);` 调用此方法会返回构造默认值 
    
    ```jsx
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        //返回threadLocal，这里其实是内部类
        return new SuppliedThreadLocal<>(supplier);
    }
    
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {
        //内部类扩展了threadLocal
        private final Supplier<? extends T> supplier;
    
        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }
    
        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }
    ```
    
6. 当前线程对象保存了threadLocalMap，调用get方法时会初始化threadLocalMap，就是以threadLocal对象为key，初始值为value放入threadLocalMap中

```java
   public T get() {
            Thread t = Thread.currentThread();
            //获取当前线程的map变量
            ThreadLocalMap map = getMap(t);
            if (map != null) {
                //获取map中的entry
                ThreadLocalMap.Entry e = map.getEntry(this);
                if (e != null) {
                    @SuppressWarnings( unchecked )
                    T result = (T)e.value;
                    return result;
                }
            }
            //如果没有entry，就初始化数据
            return setInitialValue();
     }

    private T setInitialValue() {
            T value = initialValue();
            Thread t = Thread.currentThread();
            ThreadLocalMap map = getMap(t);
            if (map != null) {
                map.set(this, value);
            } else {
                createMap(t, value);
            }
            if (this instanceof TerminatingThreadLocal) {
                TerminatingThreadLocal.register((TerminatingThreadLocal<?>) this);
            }
            return value;
     }
```
 


