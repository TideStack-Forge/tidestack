package com.ouroboros.workflow.approval.web.vo;

import java.util.List;

public class ModelCardResult {
  String title;
  List<CardItemResult> cards;

  public ModelCardResult() {
  }

  public ModelCardResult(String title, List<CardItemResult> cards) {
    this.title = title;
    this.cards = cards;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<CardItemResult> getCards() {
    return cards;
  }

  public void setCards(List<CardItemResult> cards) {
    this.cards = cards;
  }
}
