package wtf.casper.storageapi.utils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ReflectionUtil {

    private final static Table<Class<?>, String, Field> fieldCache = HashBasedTable.create();

    @SneakyThrows
    public static void setPrivateField(Object object, String field, Object newValue) {
        if (object == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }

        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }

        if (fieldCache.contains(object.getClass(), field)) {
            Field objectField = fieldCache.get(object.getClass(), field);
            objectField.set(object, newValue);
            return;
        }

        Class<?> clazz = object.getClass();
        Field objectField = clazz.getDeclaredField(field);

        fieldCache.put(clazz, field, objectField);

        objectField.setAccessible(true);
        objectField.set(object, newValue);
    }

    @SneakyThrows
    public static void setPrivateField(Class<?> clazz, String field, Object newValue) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }

        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }

        if (fieldCache.contains(clazz, field)) {
            Field objectField = fieldCache.get(clazz, field);
            objectField.set(null, newValue);
            return;
        }

        Field objectField = clazz.getDeclaredField(field);

        fieldCache.put(clazz, field, objectField);

        objectField.setAccessible(true);
        objectField.set(null, newValue);
    }

    @SneakyThrows
    public static Object getPrivateField(Object object, String field) {
        Class<?> clazz = object.getClass();

        if (fieldCache.contains(clazz, field)) {
            Field objectField = fieldCache.get(clazz, field);
            objectField.setAccessible(true);
            return objectField.get(object);
        }

        Field objectField = clazz.getDeclaredField(field);
        objectField.setAccessible(true);

        fieldCache.put(clazz, field, objectField);

        return objectField.get(object);
    }

    @SneakyThrows
    public static Object getPrivateField(Class<?> clazz, String field) {
        if (fieldCache.contains(clazz, field)) {
            Field objectField = fieldCache.get(clazz, field);
            objectField.setAccessible(true);
            return objectField.get(null);
        }

        Field objectField = clazz.getDeclaredField(field);
        objectField.setAccessible(true);

        fieldCache.put(clazz, field, objectField);

        return objectField.get(null);
    }

    /**
     * Finds a declared field in given class.
     */
    public static Field findDeclaredField(Class<?> clazz, Class<?> type, String name) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType().equals(type)) {
                field.setAccessible(true);
                return field;
            }
        }

        throw new IllegalStateException("Can't find field " + clazz.getName() + "#" + name);
    }

    /**
     * Finds a declared method in given class.
     */
    public static Method findDeclaredMethod(Class<?> clazz, Class<?>[] paramTypes, Class<?> returnType, String name) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.getReturnType().equals(returnType)) continue;
            if (!Arrays.equals(paramTypes, method.getParameterTypes()))
                continue;

            method.setAccessible(true);
            return method;
        }

        throw new IllegalStateException("Can't find method " + clazz.getName() + "." + name);
    }

    /**
     * Retrieves all methods with given annotation.
     */
    public static List<Method> getAnnotatedMethods(Class<?> clazz, Class<? extends Annotation> annotationClass) {
        Method[] declaredMethods = clazz.getDeclaredMethods();
        List<Method> methods = new ArrayList<>(declaredMethods.length);
        for (Method method : declaredMethods) {
            if (method.isAnnotationPresent(annotationClass)) {
                methods.add(method);
            }
        }
        return methods;
    }

    public static <V> Field[] getAllFields(Class<V> valueClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> clazz = valueClass;
        while (clazz != null) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    public static <V> V createInstance(Class<V> valueClass) {
        return createInstance(valueClass, new Class<?>[0], new Object[0]);
    }

    public static <V> V createInstance(Class<V> valueClass, Class<?>[] paramTypes, Object[] params) {
        try {
            return valueClass.getConstructor(paramTypes).newInstance(params);
        } catch (InstantiationException | IllegalAccessException |
                 NoSuchMethodException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static <V> V createInstance(Class<V> valueClass, Object... params) {
        Class<?>[] paramTypes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            paramTypes[i] = params[i].getClass();
        }
        return createInstance(valueClass, paramTypes, params);
    }

    /**
     * Get the generic type of class (e.g. List of String -> String)
     *
     * @param field the field to get the generic type from
     * @param index the index of the generic type
     * @return the generic type
     */
    @Nullable
    public static <T> Class<T> getGenericType(Field field, int index) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type[] types = parameterizedType.getActualTypeArguments();
            if (types.length > index) {
                return (Class<T>) types[index];
            }
        }
        return null;
    }

    /**
     * @param v         the object to get the field from
     * @param fieldName the name of the field
     * @return an optional containing the value of the field or empty if the field does not exist
     */
    public static <V> Optional<Object> getFieldValue(V v, String fieldName) {
        try {
            Field field = v.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return Optional.ofNullable(field.get(v));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * @param v         the object to get the field from
     * @param fieldName the name of the field
     * @return an optional containing the value of the field or empty if the field does not exist
     */
    public static <V, T> Optional<T> getFieldValue(V v, String fieldName, Class<T> returnType) {
        try {
            Field field = v.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Optional<Object> optional = Optional.ofNullable(field.get(v));
            return optional.map(returnType::cast);
        } catch (NoSuchFieldException | IllegalAccessException |
                 ClassCastException e) {
            return Optional.empty();
        }
    }
}
