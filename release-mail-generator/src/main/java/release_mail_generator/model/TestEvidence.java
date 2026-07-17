package release_mail_generator.model;

import lombok.Data;

@Data
public class TestEvidence {
    private String imageBase64; // data:image/...;base64,...
    private String description;
}
