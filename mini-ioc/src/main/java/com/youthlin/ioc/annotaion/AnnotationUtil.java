package com.youthlin.ioc.annotaion;

import com.youthlin.ioc.exception.NoSuchBeanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 创建： youthlin.chen
 * 时间： 2017-08-10 14:48.
 */
@SuppressWarnings("WeakerAccess")
public class AnnotationUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationUtil.class);
    private static final String FILE_SEPARATOR = System.getProperty("file.separator", "/");
    private static final String DOT_CLASS = ".class";
    private static final String DOLLAR = "$";
    private static final String DOT = ".";
    private static final FileFilter FILE_FILTER = new FileFilter() {
        @Override public boolean accept(File pathname) {
            if (pathname.isDirectory()) {
                return true;
            }
            String name = pathname.getName();
            return !name.contains(DOLLAR) && name.endsWith(DOT_CLASS);
        }
    };

    /**
     * 获取包路径之下的所有类名 不包括内部类和非 .class 结尾的类
     */
    static Set<String> getClassNames(String... basePackages) {
        Set<String> classNames = new HashSet<>();
        if (basePackages != null) {
            for (String basePackage : basePackages) {
                classNames.addAll(getClassNames(basePackage));
            }
        }
        return classNames;
    }

    private static List<String> getClassNames(String basePackage) {
        List<String> classNames = new ArrayList<>();
        try {
            Enumeration<URL> systemResources = ClassLoader.getSystemResources(basePackage.replace(DOT, FILE_SEPARATOR));
            while (systemResources.hasMoreElements()) {
                URL url = systemResources.nextElement();
                if (url != null) {
                    classNames.addAll(getClassNamesFromUrl(basePackage, url));
                }
            }
        } catch (IOException ignore) {
        }
        return classNames;
    }

    private static List<String> getClassNamesFromUrl(String basePackage, URL url) {
        List<String> classNames = new ArrayList<>();
        LOGGER.debug("scan url = {}", url);
        String protocol = url.getProtocol();
        switch (protocol) {
        case "file":
            classNames.addAll(getClassNamesFromFileSystem(basePackage, url));
            break;
        case "jar":
            classNames.addAll(getClassNamesFromJar(basePackage, url));
            break;
        default:
            LOGGER.warn("unknown protocol. [{}]", protocol);
        }
        return classNames;
    }

    private static List<String> getClassNamesFromFileSystem(String basePackage, URL url) {
        List<String> classNames = new ArrayList<>();
        String fileName = url.getFile();
        fileName = fileName.replace("%20", " ");
        File file = new File(fileName);
        File[] files = file.listFiles(FILE_FILTER);
        if (files != null) {
            for (File file1 : files) {
                classNames.addAll(getClassNamesFromFileSystem(basePackage, file1));
            }
        }

        return classNames;
    }

    private static List<String> getClassNamesFromFileSystem(String packageName, File file) {
        List<String> classNames = new ArrayList<>();
        String fileName = file.getName();
        if (file.isDirectory()) {
            File[] files = file.listFiles(FILE_FILTER);
            if (files == null) {
                return Collections.emptyList();
            }
            for (File classFile : files) {
                if (packageName.length() == 0) {
                    classNames.addAll(getClassNamesFromFileSystem(fileName, classFile));
                } else {
                    classNames.addAll(getClassNamesFromFileSystem(packageName + DOT + fileName, classFile));
                }
            }
        } else {
            LOGGER.trace("package:{} class:{}", packageName, fileName);
            classNames.add(packageName + DOT + fileName.substring(0, fileName.lastIndexOf(DOT_CLASS)));
        }
        return classNames;
    }

    private static List<String> getClassNamesFromJar(String basePackage, URL url) {
        List<String> classNames = new ArrayList<>();
        String jarFileName = url.toString();
        jarFileName = jarFileName.replace("%20", " ");
        jarFileName = jarFileName.substring("jar:file:".length());
        int indexOf = jarFileName.indexOf("!/");
        if (indexOf > 0) {
            jarFileName = jarFileName.substring(0, indexOf);
        }
        try {
            JarFile jarFile = new JarFile(jarFileName);
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String name = jarEntry.getName();
                if (name.endsWith(DOT_CLASS) && !name.contains(DOLLAR) && name.startsWith(basePackage)) {
                    name = name.replace(FILE_SEPARATOR, DOT);
                    name = name.substring(0, name.lastIndexOf(DOT_CLASS));
                    classNames.add(name);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return classNames;
    }

    /**
     * 获取字段上注解的名称
     *
     * @return 如果注解定义了名称，直接返回；否则返回空字符串
     */
    static String getAnnotationName(Field field) {
        Bean bean = field.getAnnotation(Bean.class);
        Resource resource = field.getAnnotation(Resource.class);
        String name = "";
        if (bean != null) {
            name = bean.value();
        } else if (resource != null) {
            name = resource.name();
        }
        return name;
    }

    /**
     * 获取注解中定义的名称.
     *
     * @return 如果注解定义了名称，返回名称，否则返回类名
     * @throws IllegalArgumentException 当类没有被注解时
     */
    static String getAnnotationName(Class<?> clazz) {
        Bean beanAnnotation = clazz.getAnnotation(Bean.class);
        Resource resourceAnnotation = clazz.getAnnotation(Resource.class);
        if (beanAnnotation == null && resourceAnnotation == null) {
            throw new IllegalArgumentException("No @Bean or @Resource annotation at this object.");
        }
        String name;
        if (beanAnnotation != null) {
            name = beanAnnotation.value();
        } else {
            name = resourceAnnotation.name();
        }
        if (name.isEmpty()) {
            name = clazz.getSimpleName();
        }
        return name;
    }

    /**
     * 获取字段的泛型类型.
     *
     * @param field 要处理的字段
     * @param index 泛型列表中第几个, 0开始. 如{@code Map<String, Object> map} 0 表示第一个 String.class, 1 表示第二个 Object.class
     * @return 泛型的类型
     * @throws IllegalArgumentException  当字段不是泛型时
     * @throws IndexOutOfBoundsException 当下标越界时
     */
    public static Class getGenericClass(Field field, int index) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            Type type = ((ParameterizedType) genericType).getActualTypeArguments()[index];
            if (Class.class.isAssignableFrom(type.getClass())) {
                return (Class) type;
            }
            throw new IllegalArgumentException("Field has more than one level of generic");
        }
        throw new IllegalArgumentException("field is not generic: " + field);
    }

    public static <T> T getBean(Map<Class, Object> clazzBeanMap, Class<T> clazz) {
        List<T> list = getBeans(clazzBeanMap, clazz);
        if (list.isEmpty()) {
            throw new NoSuchBeanException(clazz.getName());
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        throw new NoSuchBeanException("find more than one bean with type: " + clazz.getName());

    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getBeans(Map<Class, Object> clazzBeanMap, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        T o = (T) clazzBeanMap.get(clazz);
        if (o != null) {
            list.add(o);
        } else {
            for (Map.Entry<Class, Object> entry : clazzBeanMap.entrySet()) {
                Class aClass = entry.getKey();
                if (clazz.isAssignableFrom(aClass)) {
                    //该类可以赋值给 clazz (即，是 clazz 的子类)
                    list.add((T) entry.getValue());
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> getBeansMap(Map<Class, Object> clazzBeanMap, Class<T> clazz) {
        Map<String, T> map = new HashMap<>();
        T o = (T) clazzBeanMap.get(clazz);
        if (o != null) {
            String name = AnnotationUtil.getAnnotationName(o.getClass());
            map.put(name, o);
        } else {
            for (Map.Entry<Class, Object> entry : clazzBeanMap.entrySet()) {
                Class aClass = entry.getKey();
                if (clazz.isAssignableFrom(aClass)) {
                    //aClass 类可以赋值给 clazz (aClass 是 clazz 的子类)
                    String name = AnnotationUtil.getAnnotationName(aClass);
                    map.put(name, (T) entry.getValue());
                }
            }
        }
        return map;
    }

}
