package com.xq.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order implements Serializable {
    private Integer id;

    private Integer userid;

    private Integer goodsid;

    private Date createtime;

    private static final long serialVersionUID = 1L;
}