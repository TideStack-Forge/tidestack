package com.ouroboros.security.externalauth.ldap;

/**
 * RFC 4515 LDAP filter escaping utility.
 * Prevents LDAP filter injection attacks.
 */
public final class LdapFilterEscaper {

  private LdapFilterEscaper() {
  }

  /**
   * Escape special characters in LDAP filter values per RFC 4515.
   * Characters escaped: * ( ) \ NUL
   */
  public static String escape(String input) {
    if (input == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(input.length() + 16);
    for (int i = 0; i < input.length(); i++) {
      char ch = input.charAt(i);
      switch (ch) {
        case '*':
          sb.append("\\2a");
          break;
        case '(':
          sb.append("\\28");
          break;
        case ')':
          sb.append("\\29");
          break;
        case '\\':
          sb.append("\\5c");
          break;
        case '\0':
          sb.append("\\00");
          break;
        default:
          sb.append(ch);
          break;
      }
    }
    return sb.toString();
  }
}
