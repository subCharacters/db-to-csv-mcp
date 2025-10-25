package com.subcharacter.db_to_csv_mcp;

import com.subcharacter.db_to_csv_mcp.service.QueryService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class DbToCsvMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbToCsvMcpApplication.class, args);
    }

    @Bean
    public List<ToolCallback> danTools(QueryService queryService) {
        return List.of(ToolCallbacks.from(queryService));
    }
}
