package com.ouroboros.security.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.profile.UserProfilePolicy.Audience;
import com.ouroboros.security.profile.UserProfilePolicy.Field;

class UserProfilePolicyTest {
  @Test
  void defaultNamesExposeEditableNicknameAndReadOnlyProjections() {
    var policy = UserProfilePolicy.defaults();
    assertEquals(Set.of(Audience.SELF, Audience.ADMIN), policy.field("nickname").editableBy());
    assertEquals("小张", policy.normalize(policy.field("nickname"), "  小张  "));
    assertNull(policy.normalize(policy.field("nickname"), " "));
    for (String name : Set.of("formalName", "displayName")) {
      assertTrue(policy.field(name).editableBy().isEmpty());
      assertFalse(policy.field(name).required());
    }
  }

  @Test
  void administratorsCannotMakeDerivedNamesEditableOrRequired() {
    var defaults = UserProfilePolicy.defaults();
    for (String name : Set.of("formalName", "displayName")) {
      var field = defaults.field(name);
      var fields = new ArrayList<>(defaults.fields());
      fields.remove(field);
      fields.add(new Field(name, field.label(), "string", false,
          field.viewableBy(), Set.of(Audience.ADMIN), Map.of()));
      assertThrows(ValidationException.class, () -> new UserProfilePolicy(fields, defaults.verification()));
      fields.removeLast();
      fields.add(new Field(name, field.label(), "string", true,
          field.viewableBy(), Set.of(), Map.of()));
      assertThrows(ValidationException.class, () -> new UserProfilePolicy(fields, defaults.verification()));
    }
  }
}
