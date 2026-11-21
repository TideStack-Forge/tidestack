package com.ouroboros.widget.dashboard.service;

import io.vavr.control.Try;

import com.ouroboros.widget.Dashboard;

public interface UserDashboardService {
  Dashboard getUserDashboard(String userId);
  Try<Void> updateUserDashboard(String userId, Dashboard dashboard);
}
