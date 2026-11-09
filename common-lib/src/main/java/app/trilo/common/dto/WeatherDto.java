package app.trilo.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * One weather observation at a coordinate and point in time, normalised from the Apple WeatherKit
 * response so consumers never depend on weather-service internals.
 *
 * <p>Units follow WeatherKit: temperature in °C, wind speed/gust in km/h, pressure in hPa (mb),
 * visibility in meters, humidity/cloudCover/precipitationChance as a 0–1 fraction,
 * precipitation/snowfall intensity in mm/h.
 *
 * @param latitude requested latitude
 * @param longitude requested longitude
 * @param observedAt the instant the observation applies to (current time, or the sampled hour)
 * @param conditionCode WeatherKit condition code (e.g. {@code Clear}, {@code Rain}) — locale
 *     independent, clients map it to their own icons/labels
 * @param daylight whether it was daylight at that place and time (optional)
 * @param source {@code CURRENT} for live conditions, {@code HOURLY} for an hourly (past or future)
 *     observation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeatherDto(
    double latitude,
    double longitude,
    Instant observedAt,
    String conditionCode,
    Boolean daylight,
    Double temperature,
    Double temperatureApparent,
    Double humidity,
    Double pressure,
    Double windSpeed,
    Double windGust,
    Integer windDirection,
    Double precipitationIntensity,
    Double precipitationChance,
    String precipitationType,
    Double snowfallIntensity,
    Double cloudCover,
    Double uvIndex,
    Double visibility,
    String source) {}
