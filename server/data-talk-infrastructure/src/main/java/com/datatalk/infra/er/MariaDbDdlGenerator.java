package com.datatalk.infra.er;

import com.datatalk.domain.er.Dialect;
import org.springframework.stereotype.Component;

@Component
public class MariaDbDdlGenerator extends MySqlDdlGenerator {

    @Override
    public Dialect dialect() {
        return Dialect.MARIADB;
    }
}
