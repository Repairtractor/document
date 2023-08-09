@Component
@DependsOn("baseMappers")
public class MapperInitializer {
    private final ApplicationContext applicationContext;
     public MapperInitializer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        initializeBaseDoService();
    }
     public void initializeBaseDoService() {
        Map<String, BaseMapper> baseMappers = applicationContext.getBeansOfType(BaseMapper.class);
        for (BaseMapper mapper : baseMappers.values()) {
            BaseDoService baseDoService = new BaseDoService<>(mapper);
            applicationContext.getAutowireCapableBeanFactory().initializeBean(baseDoService, mapper.getClass().getName() + "BaseDoService");
        }
    }
}