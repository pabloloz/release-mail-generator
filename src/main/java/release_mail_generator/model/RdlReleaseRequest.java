package release_mail_generator.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RdlReleaseRequest {

    // Jackson deserializa la lista correctamente desde JSON
    private List<RdlItem> rdls = new ArrayList<>();
}
