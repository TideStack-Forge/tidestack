package com.ouroboros.uischema;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.ouroboros.uimodel.microweb.MicroWebModel;
import com.ouroboros.uimodel.microweb.MicroWebModelProvider;

public class TestMicroWebModelProvider implements MicroWebModelProvider {

  @Override
  public List<? extends MicroWebModel> getAllMicroWebModel() {
    List<MicroWebModel> models = new ArrayList<MicroWebModel>();
    models.add(new TestMicroWebModel("app-one", MicroWebModel.MicroWebType.APP));
    models.add(new TestMicroWebModel("library-one", MicroWebModel.MicroWebType.LIBRARY));
    models.add(new TestMicroWebModel("library-two", MicroWebModel.MicroWebType.LIBRARY));
    return models;
  }

  private static final class TestMicroWebModel implements MicroWebModel {
    private final String name;
    private final MicroWebType type;

    private TestMicroWebModel(String name, MicroWebType type) {
      this.name = name;
      this.type = type;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public MicroWebType getType() {
      return type;
    }

    @Override
    public String getEntry() {
      return "/" + name;
    }

    @Override
    public String getPublicPath() {
      return "/public/" + name;
    }

    @Override
    public InputStream getPackageContent() {
      return null;
    }
  }
}
