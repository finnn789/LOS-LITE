package org.project.loslite.controller;

import lombok.RequiredArgsConstructor;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.dto.EndpointDescriptor;
import org.project.loslite.dto.EndpointPathRequest;
import org.project.loslite.service.EndpointDiscoveryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API buat konsumen eksternal (Auto-Layout) nemuin endpoint LOS-LITE dan
 * skeleton schema-nya, dipakai buat auto-generate form - BUKAN dipanggil
 * user aplikasi biasa. Diproteksi role SERVICE_SCHEMA_READ lewat
 * AutoLayoutServiceTokenFilter (lihat SecurityConfig), terpisah dari sesi
 * login JWT user biasa.
 */
@RestController
@RequestMapping(path = "/api/los/endpoints")
@RequiredArgsConstructor
public class EndpointDiscoveryController {

    private final EndpointDiscoveryService endpointDiscoveryService;

    @GetMapping(path = "/form", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<EndpointDescriptor>> listFormEndpoints() {
        return ApiResponse.success("Daftar endpoint form", endpointDiscoveryService.listFormEndpoints());
    }

    @GetMapping(path = "/actions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<EndpointDescriptor>> listActionEndpoints() {
        return ApiResponse.success("Daftar endpoint aksi", endpointDiscoveryService.listActionEndpoints());
    }

    @PostMapping(path = "/path", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> getSchema(@RequestBody EndpointPathRequest body) {
        return endpointDiscoveryService.getSchemaByPath(body.path(), body.method());
    }
}
