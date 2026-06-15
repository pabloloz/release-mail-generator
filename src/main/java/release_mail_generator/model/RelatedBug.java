package release_mail_generator.model;

import lombok.Data;

@Data
public class RelatedBug {
    private String identifier;
    private String description;
    private String bugStatus;
}
