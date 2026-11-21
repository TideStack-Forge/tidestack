package com.ouroboros.logicflow.manage;

import java.util.Map;
import java.util.Optional;
import com.ouroboros.data.station.DataStation;
import com.ouroboros.data.station.DataStationProvider;

/** Test-only assembly through the platform's real station SPI. */
public final class RuntimeFlowTestStationProvider implements DataStationProvider {
  static volatile DataStation<?> station;
  @Override public Optional<DataStation<?>> getDataStation(String name) {
    return Optional.<DataStation<?>>ofNullable(station).filter(value -> value.getName().equals(name));
  }
  @Override public Map<String, DataStation<?>> getDataStationMap() {
    var value = station;
    return value == null ? Map.of() : Map.of(value.getName(), value);
  }
}
