package org.fhir.auth.entity;


import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter @Setter
public class Group {
    private String name;
    private List<String> members;
    private List<String> organizations;
}