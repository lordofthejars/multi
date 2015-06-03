package com.scytl.multi;


import javax.sql.DataSource;

public class CreatedDataSourceEvent {

    private DataSource dataSource;

    public CreatedDataSourceEvent(DataSource event) {
        this.dataSource = event;
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}
