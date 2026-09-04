package org.project.loslite.dto;

/** method opsional - dipakai buat disambiguasi kalau satu path punya lebih dari satu
 * handler (mis. GET dan POST sama-sama di "/applicants"). Null berarti ambil yang pertama
 * ketemu, sama seperti perilaku lama. */
public record EndpointPathRequest(String path, String method) {
}
