package app.trilo.common.dto;

/**
 * A single file to bundle into a data export zip.
 *
 * @param storageKey object key relative to the shared storage bucket (e.g. {@code
 *     gpx/{tripId}/x.gpx})
 * @param zipPath destination path inside the export zip (e.g. {@code gpx/{tripId}/track.gpx})
 */
public record ExportFile(String storageKey, String zipPath) {}
