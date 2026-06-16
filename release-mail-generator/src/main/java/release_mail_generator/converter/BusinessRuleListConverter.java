package release_mail_generator.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import release_mail_generator.model.BusinessRule;

import java.util.ArrayList;
import java.util.List;

@Converter
public class BusinessRuleListConverter implements AttributeConverter<List<BusinessRule>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<BusinessRule> attribute) {
        if (attribute == null || attribute.isEmpty()) return "[]";
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<BusinessRule> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<BusinessRule>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
