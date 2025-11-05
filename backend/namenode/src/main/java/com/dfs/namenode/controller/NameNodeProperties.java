package com.dfs.namenode.controller;

import org.springframework.boot.context.properties.ConfigurationProperties;
// Remove @Component import
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// Remove @Component annotation - only keep @ConfigurationProperties
@ConfigurationProperties(prefix = "namenode")
public class NameNodeProperties {
    private String id;
    private String host;
    private String peers;
    
    public String getId() { 
        return id; 
    }
    
    public void setId(String id) { 
        this.id = id; 
    }
    
    public String getHost() { 
        return host; 
    }
    
    public void setHost(String host) { 
        this.host = host; 
    }
    
    public List<String> getPeers() { 
        if (peers == null || peers.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(peers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
    
    public void setPeers(String peers) { 
        this.peers = peers; 
    }
}