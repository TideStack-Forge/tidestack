package com.ouroboros.uimodel.microweb;

import java.io.File;
import java.io.InputStream;

import org.springframework.stereotype.Service;

/**
 * @author liansz
 */
@Service
public class MicroWebDeployServiceImpl implements MicroWebDeployService {


  @Override
  public void deploy(String publicPath, InputStream inputStream) {

  }

  @Override
  public void deploy(String publicPath, File source) {

  }

  @Override
  public void remove(String publicPath) {

  }
}
