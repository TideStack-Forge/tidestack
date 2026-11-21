package com.ouroboros.data.initialization;

import java.util.List;

public interface InitializationPlanProvider {
  List<DataInitializationAsset> loadPlans();
}
