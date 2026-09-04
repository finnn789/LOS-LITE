package org.project.loslite.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EndpointDescriptor {
    private String path;
    private String httpMethods;
    private String controller;
    private String handler;
}
