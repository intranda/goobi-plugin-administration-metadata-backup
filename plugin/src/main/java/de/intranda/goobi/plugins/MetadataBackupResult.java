package de.intranda.goobi.plugins;

import lombok.Data;

@Data
public class MetadataBackupResult {
    private String title = "";
    private int id;
    private String status = "OK";
    private String message = "";
}