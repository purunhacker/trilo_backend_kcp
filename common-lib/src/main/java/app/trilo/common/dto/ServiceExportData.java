package app.trilo.common.dto;

import java.util.List;

/**
 * Partial export payload returned by a service's internal export endpoint.
 *
 * <p>{@code json} is the already-serialized JSON document for that service's domain (written
 * verbatim into the export zip). {@code files} lists storage objects (relative to the shared
 * bucket) that should be bundled into the zip at the given paths.
 */
public record ServiceExportData(String json, List<ExportFile> files) {}
