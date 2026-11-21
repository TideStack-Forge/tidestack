package com.ouroboros.businessmodel;

public interface BusinessRelationView {
  String getName();
  String getTitle();
  String getType();
  String getRelatedModelName();
  BusinessModel getRelatedModel();
  String getReferenceKey();
}
