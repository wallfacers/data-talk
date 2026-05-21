package com.datatalk.domain.er;

import java.util.List;

public record ErTableMeta(
    String name,
    String comment,
    List<ErColumnMeta> columns,
    List<ErRelation> fkOut
) {}
