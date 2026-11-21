package com.ouroboros.security.authority;

import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.SuperAccessControlParams;

/**
 * @author liansz
 */
public interface OuroborosGrantedAuthority {

  String getAuthority();

  /**
   * 获取授权对象
   *
   * @return
   */
  Authority getAuthorityObject();

  /**
   * 获取授权控制参数
   *
   * @return
   */
  AccessControlParams getAccessControlParams();

  /**
   * 合并两个授权对象
   *
   * @param authorityA 授权对象A
   * @param authorityB 授权对象B
   * @return 合并后的授权对象
   */
  static OuroborosGrantedAuthority merge(OuroborosGrantedAuthority authorityA, OuroborosGrantedAuthority authorityB) {
    // 判断是否时同名权限
    if (!authorityA.getAuthority().equals(authorityB.getAuthority())) {
    }
    if (authorityA instanceof SuperAccessControlParams) {
      return authorityA;
    }
    if (authorityB instanceof SuperAccessControlParams) {
      return authorityB;
    }
    return new MergedAuthority(authorityA, authorityB);
  }

  class MergedAuthority implements OuroborosGrantedAuthority {
    private final OuroborosGrantedAuthority authorityA;
    private final OuroborosGrantedAuthority authorityB;

    public MergedAuthority(OuroborosGrantedAuthority authorityA, OuroborosGrantedAuthority authorityB) {
      this.authorityA = authorityA;
      this.authorityB = authorityB;
    }

    @Override
    public String getAuthority() {
      return authorityA.getAuthority();
    }

    @Override
    public Authority getAuthorityObject() {
      return authorityA.getAuthorityObject();
    }

    @Override
    public AccessControlParams getAccessControlParams() {
      return AccessControlParams.or(authorityA.getAccessControlParams(), authorityB.getAccessControlParams());
    }
  }
}
