package com.ouroboros.security.profile;

import java.net.IDN;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.exception.ValidationException;

/** A single definition drives server validation and the supported UI field projection. */
public record UserProfilePolicy(List<Field> fields, Map<String, String> verification) {
  public enum Audience { SELF, ADMIN }
  public record Field(String name, String label, String type, boolean required,
      Set<Audience> viewableBy, Set<Audience> editableBy, Map<String, Object> validators) {
    public Field {
      viewableBy = viewableBy == null ? Set.of() : Set.copyOf(viewableBy);
      editableBy = editableBy == null ? Set.of() : Set.copyOf(editableBy);
      validators = validators == null ? Map.of() : Map.copyOf(validators);
    }
  }
  private static final Map<String, String> FIXED = Map.of("nickname", "string", "formalName", "string",
      "displayName", "string", "avatar", "string", "email", "email", "phoneNumber", "phone");
  private static final Set<String> DERIVED = Set.of("formalName", "displayName");
  private static final Set<String> RESERVED = Set.of("publicsubjectid", "password", "profilerevision", "profilevalues",
      "emailverified", "phonenumberverified", "useridentity", "roles", "subjecttype", "subjectid", "employeeid",
      "isenabled", "isdeleted", "createdat", "updatedat", "deletedat", "createdby", "updatedby", "deletedby");

  public UserProfilePolicy {
    if (fields == null || verification == null || fields.size() > 100) { throw invalid("资料规则不完整"); }
    fields = List.copyOf(fields);
    verification = Map.copyOf(verification);
    var names = new HashSet<String>();
    for (Field field : fields) {
      if (field == null || field.name() == null || !field.name().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
          || !names.add(field.name().toLowerCase(java.util.Locale.ROOT))
          || RESERVED.contains(field.name().toLowerCase(java.util.Locale.ROOT))
          || field.label() == null || field.label().isBlank() || field.label().length() > 100
          || field.type() == null || !Set.of("string", "enum", "date", "email", "phone").contains(field.type())
          || !field.viewableBy().containsAll(field.editableBy())) { throw invalid("字段名称、类型或访问规则无效"); }
      for (var fixed : FIXED.entrySet()) {
        if (fixed.getKey().equalsIgnoreCase(field.name())
            && (!fixed.getKey().equals(field.name()) || !fixed.getValue().equals(field.type()))) {
          throw invalid("固定字段不能改名或改变类型");
        }
      }
      if (field.name().equals("avatar") && !field.editableBy().isEmpty()) { throw invalid("头像本期只读"); }
      if (DERIVED.contains(field.name()) && (!field.editableBy().isEmpty() || field.required())) {
        throw invalid("正式名称和显示名称只读，且不能设为必填");
      }
      if (!Set.of("maxLength", "minLength", "options").containsAll(field.validators().keySet())) {
        throw invalid("不支持的字段校验规则");
      }
      for (String key : List.of("minLength", "maxLength")) {
        if (field.validators().containsKey(key) && (!(field.validators().get(key) instanceof Number n)
            || n.intValue() < 0 || n.intValue() > 2000 || n.doubleValue() != n.intValue())) {
          throw invalid("长度限制无效");
        }
      }
      if (field.validators().containsKey("minLength") && field.validators().containsKey("maxLength")
          && ((Number) field.validators().get("minLength")).intValue() > ((Number) field.validators().get("maxLength")).intValue()) {
        throw invalid("最小长度不能超过最大长度");
      }
      if (field.type().equals("enum") && (!(field.validators().get("options") instanceof List<?> options)
          || options.isEmpty() || options.size() > 100 || options.stream().anyMatch(o -> !(o instanceof String)))) {
        throw invalid("枚举必须提供选项");
      }
    }
    if (!names.containsAll(FIXED.keySet().stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).toList())
        || !verification.keySet().equals(Set.of("email", "phone"))
        || verification.values().stream().anyMatch(v -> !Set.of("NONE", "REQUIRED").contains(v))) {
      throw invalid("固定字段或联系方式策略缺失");
    }
  }

  public Field field(String name) {
    return fields.stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow(() -> invalid("未知资料字段"));
  }

  public Object normalize(Field field, Object value) {
    if (value == null) { return null; }
    if (!(value instanceof String text)) { throw invalid(field.label() + "必须为文本"); }
    text = text.trim();
    if (text.isEmpty()) { return null; }
    int max = ((Number) field.validators().getOrDefault("maxLength", 2000)).intValue();
    int min = ((Number) field.validators().getOrDefault("minLength", 0)).intValue();
    if (text.length() < min || text.length() > max || text.chars().anyMatch(Character::isISOControl)) {
      throw invalid(field.label() + "长度或字符无效");
    }
    switch (field.type()) {
      case "email" -> {
        if (!text.matches("[^\\s@,;<>]+@[^\\s@,;<>]+") || text.length() > 254) { throw invalid("邮箱必须是单个有效地址"); }
        int at = text.lastIndexOf('@');
        String domain;
        try { domain = IDN.toASCII(text.substring(at + 1), IDN.USE_STD3_ASCII_RULES).toLowerCase(java.util.Locale.ROOT); }
        catch (IllegalArgumentException error) { throw invalid("邮箱域名无效"); }
        if (!domain.contains(".") || text.substring(0, at).length() > 64) { throw invalid("邮箱地址无效"); }
        text = text.substring(0, at) + "@" + domain;
      }
      case "phone" -> { if (!text.matches("\\+[1-9][0-9]{1,14}")) { throw invalid("电话必须采用 E.164 格式"); } }
      case "enum" -> {
        if (!((List<?>) field.validators().get("options")).contains(text)) { throw invalid(field.label() + "选项无效"); }
      }
      case "date" -> {
        try { if (!LocalDate.parse(text).toString().equals(text)) { throw invalid("日期格式无效"); } }
        catch (java.time.format.DateTimeParseException error) { throw invalid("日期格式必须为 YYYY-MM-DD"); }
      }
      default -> { }
    }
    return text;
  }

  public static UserProfilePolicy defaults() {
    var both = Set.of(Audience.SELF, Audience.ADMIN);
    var fields = new ArrayList<Field>();
    fields.add(new Field("nickname", "昵称", "string", false, both, both, Map.of("maxLength", 100)));
    fields.add(new Field("formalName", "正式名称", "string", false, both, Set.of(), Map.of()));
    fields.add(new Field("displayName", "显示名称", "string", false, both, Set.of(), Map.of()));
    fields.add(new Field("avatar", "头像", "string", false, both, Set.of(), Map.of()));
    fields.add(new Field("email", "联系邮箱", "email", false, both, both, Map.of()));
    fields.add(new Field("phoneNumber", "联系电话", "phone", false, both, both, Map.of()));
    fields.add(new Field("gender", "性别", "enum", false, both, Set.of(),
        Map.of("options", List.of("male", "female", "other", "unspecified"))));
    fields.add(new Field("birthdate", "出生日期", "date", false, both, Set.of(), Map.of()));
    return new UserProfilePolicy(fields, Map.of("email", "NONE", "phone", "NONE"));
  }

  private static ValidationException invalid(String message) { return new ValidationException("[PROFILE_INVALID] " + message); }
}
