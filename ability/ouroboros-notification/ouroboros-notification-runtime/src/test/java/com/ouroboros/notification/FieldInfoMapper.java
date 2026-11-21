package com.ouroboros.notification;

/**
 * @author liansz
 */

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.ouroboros.util.Json;

public class FieldInfoMapper {

  public static Map<String, Object> mapFields(Class<?> clazz) {
    Map<String, Object> fieldMap = new HashMap<>();
    Field[] fields = clazz.getDeclaredFields();

    for (Field field : fields) {
      String fieldName = field.getName();
      Class<?> fieldType = field.getType();

      if (isBasicType(fieldType)) {
        fieldMap.put(fieldName, fieldType.getName());
      } else {
        fieldMap.put(fieldName, mapFields(fieldType));
      }
    }

    return fieldMap;
  }

  public static boolean isBasicType(Class<?> clazz) {
    return clazz.isPrimitive() || clazz == String.class ||
        clazz == Integer.class || clazz == Long.class ||
        clazz == Float.class || clazz == Double.class ||
        clazz == Short.class || clazz == Byte.class ||
        clazz == Character.class || clazz == Boolean.class;
  }

  public static void main(String[] args) {
    // 示例：输入一个类
    Class<?> myClass = MyClass.class;

    // 获取字段信息并映射为Map
    Map<String, Object> fieldInfoMap = mapFields(myClass);

    System.out.println(Json.toJsonString(fieldInfoMap));
  }

  // 示例类
  static class MyClass {
    int primitiveField;
    String stringField;
    AnotherClass anotherClassField;

    public int getPrimitiveField() {
      return primitiveField;
    }

    public void setPrimitiveField(int primitiveField) {
      this.primitiveField = primitiveField;
    }

    public String getStringField() {
      return stringField;
    }

    public void setStringField(String stringField) {
      this.stringField = stringField;
    }

    public AnotherClass getAnotherClassField() {
      return anotherClassField;
    }

    public void setAnotherClassField(AnotherClass anotherClassField) {
      this.anotherClassField = anotherClassField;
    }
  }

  static class AnotherClass {
    double doubleField;
    boolean booleanField;

    public double getDoubleField() {
      return doubleField;
    }

    public void setDoubleField(double doubleField) {
      this.doubleField = doubleField;
    }

    public boolean isBooleanField() {
      return booleanField;
    }

    public void setBooleanField(boolean booleanField) {
      this.booleanField = booleanField;
    }
  }
}

