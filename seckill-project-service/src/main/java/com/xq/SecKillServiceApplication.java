package com.xq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "com.xq.mapper")//对mapper接口进行批量扫描
public class  SecKillServiceApplication
{
    public static void main( String[] args )
    {

        SpringApplication.run(SecKillServiceApplication.class, args);
    }
}
