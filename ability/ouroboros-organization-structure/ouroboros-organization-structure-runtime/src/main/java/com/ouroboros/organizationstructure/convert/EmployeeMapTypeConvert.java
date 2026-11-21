package com.ouroboros.organizationstructure.convert;

import java.lang.reflect.Type;
import java.util.Map;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.service.impl.OrganizationModelResolver;
import com.ouroboros.util.Maps;
import com.ouroboros.util.Types;
import com.ouroboros.util.map.MapDeserializer;


/**
 * @author lianshuze
 * @since 2026/3/3 10:43
 **/
public class EmployeeMapTypeConvert implements MapDeserializer {


    @Override
    public boolean support(Type targetType) {
        return Employee.class.isAssignableFrom(Types.rawClass(targetType));
    }

    @Override
    public Employee deserialize(Type targetType, Object value) {
        if (value == null) {
            return null;
        }

        OrganizationModelResolver resolver = SpringContextUtil.getBean(OrganizationModelResolver.class);
        return resolver.toEmployeeModel(
                Maps.tryToBean(EmployeeEntity.class, (Map<String, Object>) value).getOrElse(() -> null)
        );
    }
}
