package com.xinpai.spreadsheet.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateUtil {
  public static LocalDateTime toLocalDateTime(Date date) {
    return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
  }

  public static LocalDateTime toLocalDateTime(LocalDate date, int hour, int minute, int second) {
    return date.atTime(hour, minute, second);
  }

  public static LocalDateTime toLocalDateTime(LocalDate date) {
    return toLocalDateTime(date, 0, 0, 0);
  }

  public static LocalDateTime toLocalDateTime(String isoDateString) {
    return LocalDateTime.parse(isoDateString);
  }

  public static LocalDateTime toLocalDateTime(String dateString, String format) {
    return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern(format));
  }

  public static LocalDate toLocalDate(Date date) {
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }

  public static LocalDate toLocalDate(LocalDateTime date) {
    return date.toLocalDate();
  }

  public static LocalDate toLocalDate(String isoDateString) {
    return LocalDate.parse(isoDateString);
  }

  public static LocalDate toLocalDate(String dateString, String format) {
    return LocalDate.parse(dateString, DateTimeFormatter.ofPattern(format));
  }

  public static Date toDate(LocalDateTime date) {
    return Date.from(date.atZone(ZoneId.systemDefault()).toInstant());
  }

  public static Date toDate(LocalDate date) {
    return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  public static Date toDate(String isoDateString) {
    return Date.from(LocalDateTime.parse(isoDateString).atZone(ZoneId.systemDefault()).toInstant());
  }

  public static Date toDate(String dateString, String format) {
    return Date.from(LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern(format)).atZone(ZoneId.systemDefault())
        .toInstant());
  }

  public static java.sql.Date toSqlDate(LocalDate date) {
    return java.sql.Date.valueOf(date);
  }

  public static java.sql.Date toSqlDate(LocalDateTime date) {
    return java.sql.Date.valueOf(date.toLocalDate());
  }

  public static java.sql.Date toSqlDate(Date date) {
    return new java.sql.Date(date.getTime());
  }

  public static java.sql.Date toSqlDate(String isoDateString) {
    return java.sql.Date.valueOf(toLocalDate(isoDateString));
  }

  public static java.sql.Date toSqlDate(String dateString, String format) {
    return java.sql.Date.valueOf(toLocalDate(dateString, format));
  }

  public static String toString(Date date) {
    return date.toInstant().toString();
  }

  public static String toString(LocalDateTime date) {
    return date.toString();
  }

  public static String toString(LocalDate date) {
    return date.toString();
  }

  public static String toString(Date date, String format) {
    return date.toInstant().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(format));
  }

  public static String toString(LocalDateTime date, String format) {
    return date.format(DateTimeFormatter.ofPattern(format));
  }

  public static String toString(LocalDate date, String format) {
    return date.format(DateTimeFormatter.ofPattern(format));
  }

  public static LocalDateTime addTime(LocalDateTime date, int hours, int minutes, int seconds) {
    return date.plusHours(hours).plusMinutes(minutes).plusSeconds(seconds);
  }

  public static Date addTime(Date date, int hours, int minutes, int seconds) {
    return new Date(date.getTime() + (hours * 60 * 60 * 1000) + (minutes * 60 * 1000) + (seconds * 1000));
  }

  public static java.sql.Date addTime(java.sql.Date date, int hours, int minutes, int seconds) {
    return new java.sql.Date(date.getTime() + (hours * 60 * 60 * 1000) + (minutes * 60 * 1000) + (seconds * 1000));
  }

  public static LocalDateTime setTime(LocalDateTime date, int hours, int minutes, int seconds) {
    return date.withHour(hours).withMinute(minutes).withSecond(seconds);
  }

  public static Date setTime(Date date, int hours, int minutes, int seconds) {
    return toDate(toLocalDate(date).atTime(hours, minutes, seconds));
  }

  public static java.sql.Date setTime(java.sql.Date date, int hours, int minutes, int seconds) {
    return toSqlDate(toLocalDate(date).atTime(hours, minutes, seconds));
  }

  public static Date addDays(Date date, int days) {
    return new Date(date.getTime() + (days * 24 * 60 * 60 * 1000));
  }

  public static java.sql.Date addDays(java.sql.Date date, int days) {
    return new java.sql.Date(date.getTime() + (days * 24 * 60 * 60 * 1000));
  }

  public static Long getTimestamp() {
    return System.currentTimeMillis();
  }

  public static Long getTimestamp(Date date) {
    return date.getTime();
  }

  public static Long getTimestamp(LocalDateTime date) {
    return date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  public static Long getTimestamp(LocalDate date) {
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  public static Long getUnitTimestamp(Date date) {
    return date.getTime() / 1000;
  }

  public static Long getUnitTimestamp(LocalDateTime date) {
    return date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000;
  }

  public static Long getUnitTimestamp(LocalDate date) {
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000;
  }

  public static LocalDate getYearStartDay(LocalDate date) {
    return LocalDate.of(date.getYear(), 1, 1);
  }

  public static LocalDate getYearStartDay(LocalDateTime date) {
    return LocalDate.of(date.getYear(), 1, 1);
  }

  public static LocalDate getYearEndDay(LocalDate date) {
    return LocalDate.of(date.getYear(), 12, 31);
  }

  public static LocalDate getYearEndDay(LocalDateTime date) {
    return LocalDate.of(date.getYear(), 12, 31);
  }

  public static LocalDateTime getYearStart(LocalDateTime date) {
    return LocalDateTime.of(date.getYear(), 1, 1, 0, 0, 0);
  }

  public static LocalDateTime getYearStart(LocalDate date) {
    return LocalDateTime.of(date.getYear(), 1, 1, 0, 0, 0);
  }

  public static LocalDateTime getYearEnd(LocalDateTime date) {
    return LocalDateTime.of(date.getYear(), 12, 31, 23, 59, 59);
  }

  public static LocalDateTime getYearEnd(LocalDate date) {
    return LocalDateTime.of(date.getYear(), 12, 31, 23, 59, 59);
  }

  public static LocalDate getMonthStartDay(LocalDate date) {
    return LocalDate.of(date.getYear(), date.getMonthValue(), 1);
  }

  public static LocalDate getMonthEndDay(LocalDate date) {
    return LocalDate.of(date.getYear(), date.getMonthValue(), date.lengthOfMonth());
  }

  public static LocalDateTime getMonthStart(LocalDateTime date) {
    return LocalDateTime.of(date.getYear(), date.getMonth(), 1, 0, 0, 0);
  }

  public static LocalDateTime getMonthStart(LocalDate date) {
    return LocalDateTime.of(date.getYear(), date.getMonth(), 1, 0, 0, 0);
  }

  public static LocalDateTime getMonthEnd(LocalDateTime date) {
    return LocalDateTime.of(date.getYear(), date.getMonth(), date.toLocalDate().lengthOfMonth(), 23, 59, 59);
  }

  public static LocalDateTime getMonthEnd(LocalDate date) {
    return LocalDateTime.of(date.getYear(), date.getMonth(), date.lengthOfMonth(), 23, 59, 59);
  }

  public static LocalDate getWeekStartDay(LocalDate date) {
    return date.with(DayOfWeek.MONDAY);
  }

  public static LocalDate getWeekEndDay(LocalDate date) {
    return date.with(DayOfWeek.SUNDAY);
  }

  public static LocalDateTime getWeekStart(LocalDateTime date) {
    return date.with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0);
  }

  public static LocalDateTime getWeekStart(LocalDate date) {
    return date.with(DayOfWeek.MONDAY).atTime(LocalTime.MIN);
  }

  public static LocalDateTime getWeekEnd(LocalDateTime date) {
    return date.with(DayOfWeek.SUNDAY).with(LocalTime.MAX);
  }

  public static LocalDateTime getWeekEnd(LocalDate date) {
    return date.with(DayOfWeek.SUNDAY).atTime(LocalTime.MAX);
  }

  public static LocalDateTime getDayStart(LocalDateTime date) {
    return LocalDateTime.of(date.toLocalDate(), LocalTime.MIN);
  }

  public static LocalDateTime getDayStart(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.MIN);
  }

  public static LocalDateTime getDayEnd(LocalDateTime date) {
    return LocalDateTime.of(date.toLocalDate(), LocalTime.MAX);
  }

  public static LocalDateTime getDayEnd(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.MAX);
  }

  public static LocalDateTime getThisYearStart() {
    return LocalDateTime.of(LocalDate.now().withDayOfYear(1), LocalTime.MIN);
  }

  public static LocalDateTime getThisYearEnd() {
    return LocalDate.now().withMonth(12).withDayOfMonth(31).atTime(LocalTime.MAX);
  }

  public static LocalDateTime getThisMonthStart() {
    return LocalDateTime.of(LocalDate.now().getYear(), LocalDate.now().getMonthValue(), 1, 0, 0, 0);
  }

  public static LocalDateTime getThisMonthEnd() {
    return LocalDateTime.of(LocalDate.now().getYear(), LocalDate.now().getMonthValue(), LocalDate.now().lengthOfMonth(),
        23, 59, 59);
  }

  public static LocalDateTime getTodayStart() {
    return LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
  }

  public static LocalDateTime getTodayEnd() {
    return LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
  }

  public static LocalDateTime getLastYearStart() {
    return LocalDateTime.of(LocalDate.now().getYear() - 1, 1, 1, 0, 0, 0);
  }

  public static LocalDateTime getLastYearEnd() {
    return LocalDateTime.of(LocalDate.now().getYear() - 1, 12, 31, 23, 59, 59);
  }

  public static LocalDateTime getLastMonthStart(LocalDate date) {
    return date.minusDays(date.getDayOfMonth() + 1).withDayOfMonth(1).atTime(LocalTime.MIN);
  }

  public static LocalDateTime getLastMonthStart(LocalDateTime date) {
    return date.minusDays(date.getDayOfMonth() + 1).withDayOfMonth(1).with(LocalTime.MIN);
  }

  public static LocalDateTime getLastMonthEnd(LocalDate date) {
    return date.minusDays(date.getDayOfMonth() + 1).atTime(LocalTime.MAX);
  }

  public static LocalDateTime getLastMonthEnd(LocalDateTime date) {
    return date.minusDays(date.getDayOfMonth() + 1).with(LocalTime.MAX);
  }

  public static LocalDate getThisWeekday(LocalDate date, Integer weekday) {
    return date.with(DayOfWeek.of(weekday));
  }

  public static LocalDate getThisWeekday(LocalDateTime date, Integer weekday) {
    return date.with(DayOfWeek.of(weekday)).toLocalDate();
  }

  public static LocalDate getNextWeekday(LocalDate date, Integer weekday) {
    int day = date.getDayOfWeek().getValue();
    if (day < weekday) {
      return date.plusDays(weekday - day);
    } else if (day > weekday) {
      return date.plusDays(weekday - day + 7);
    } else {
      return date;
    }
  }

  public static LocalDate getPrevWeekday(LocalDate date, Integer weekday) {
    int day = date.getDayOfWeek().getValue();
    if (day < weekday) {
      return date.plusDays(weekday - day - 7);
    } else if (day > weekday) {
      return date.plusDays(weekday - day);
    } else {
      return date;
    }
  }

  public static LocalDate getNextWeekday(Integer weekday) {
    return getNextWeekday(LocalDate.now(), weekday);
  }

  public static LocalDate getPrevWeekday(Integer weekday) {
    return getPrevWeekday(LocalDate.now(), weekday);
  }

  public static LocalDate today() {
    return LocalDate.now();
  }

  public static LocalDateTime now() {
    return LocalDateTime.now();
  }

  public static Date getCurrentDate() {
    return new Date();
  }
}