package release_mail_generator.service;

import release_mail_generator.model.UatBlock;
import release_mail_generator.model.UatRequest;
import release_mail_generator.util.HtmlSanitizer;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

/**
 * Modelo ya resuelto del correo VoBo UAT estructurado (defaults aplicados, numeración calculada,
 * imágenes validadas). Los renderizadores (HTML, texto, Markdown, PDF) solo leen este modelo.
 */
record UatMailModel(
        String greeting,
        String rfcLabel,
        String rfcName,
        String dateText,
        String attachment,
        List<String> relatedRfcs,
        String requirementsIntro,
        String legacyRequirements,
        List<String> legacyRequirementImages,
        List<Req> requirements,
        List<Example> examples,
        List<UatBlock> legacyBlocks,
        String note,
        String module,
        List<Step> steps,
        String closing,
        String subject) {

    record Req(int number, String title, String description, List<String> bullets, String image) {}
    record KeyValue(String key, String value) {}
    record Example(String heading, List<KeyValue> pairs, String image) {}
    record Step(int number, String text, String image, int maxWidth) {}

    static final String DEFAULT_MODULE = "Servicios_UAT";
    static final String DEFAULT_SERVER = "MEGANG-384";
    static final String DEFAULT_GREETING = "Buenas tardes";
    static final String DEFAULT_CLOSING = "Quedo pendiente por cualquier duda o comentario.";

    private static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final Pattern ISO_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern STEP_TOKEN = Pattern.compile("^@step([1-3])$");

    /** True si la solicitud trae cualquier dato del formato estructurado. */
    static boolean isStructured(UatRequest r) {
        return !blank(r.getRecipient()) || !blank(r.getPublishDate()) || !blank(r.getRequirementsIntro())
                || !blank(r.getValidationModule()) || !blank(r.getValidationServer())
                || r.getRequirementsStart() != null
                || nonEmpty(r.getRelatedRfcs()) || nonEmpty(r.getRequirements())
                || nonEmpty(r.getValidationExamples()) || nonEmpty(r.getValidationSteps());
    }

    static UatMailModel from(UatRequest r, IntFunction<String> stepImage) {
        String rfcLabel = rfcLabel(r.getRfcNumber());
        String name = clean(r.getRfcName());

        String saludo = clean(r.getSaludo()).replaceAll("[\\s.,;:]+$", "");
        if (saludo.isEmpty()) saludo = DEFAULT_GREETING;
        String recipient = clean(r.getRecipient()).replaceAll("[\\s.,;:]+$", "");
        String greeting = saludo + (recipient.isEmpty() ? "" : ", " + recipient) + ".";

        List<String> related = new ArrayList<>();
        if (r.getRelatedRfcs() != null) {
            for (String rel : r.getRelatedRfcs()) {
                String c = clean(rel).replaceFirst("(?i)^rfc\\s*", "");
                if (!c.isEmpty() && !related.contains(c)) related.add(c);
            }
        }

        List<Req> reqs = new ArrayList<>();
        int number = r.getRequirementsStart() != null && r.getRequirementsStart() >= 0 ? r.getRequirementsStart() : 1;
        if (r.getRequirements() != null) {
            for (UatRequest.Requirement q : r.getRequirements()) {
                if (q == null) continue;
                List<String> bullets = cleanList(q.getBullets());
                String image = HtmlSanitizer.safeImageDataUri(q.getImage());
                if (blank(q.getTitle()) && blank(q.getDescription()) && bullets.isEmpty() && image.isEmpty()) continue;
                reqs.add(new Req(number++, clean(q.getTitle()), clean(q.getDescription()), bullets, image));
            }
        }

        List<Example> examples = new ArrayList<>();
        if (r.getValidationExamples() != null) {
            List<Example> raw = new ArrayList<>();
            for (UatRequest.Example e : r.getValidationExamples()) {
                if (e == null) continue;
                List<KeyValue> pairs = new ArrayList<>();
                if (e.getPairs() != null) {
                    for (UatRequest.KeyValue kv : e.getPairs()) {
                        if (kv != null && (!blank(kv.getKey()) || !blank(kv.getValue())))
                            pairs.add(new KeyValue(clean(kv.getKey()), clean(kv.getValue())));
                    }
                }
                String image = HtmlSanitizer.safeImageDataUri(e.getImage());
                if (!pairs.isEmpty() || !image.isEmpty()) raw.add(new Example(clean(e.getTitle()), pairs, image));
            }
            for (int i = 0; i < raw.size(); i++) {
                Example e = raw.get(i);
                String heading = !e.heading().isEmpty() ? e.heading() : (raw.size() > 1 ? "Ejemplo " + (i + 1) + ":" : null);
                examples.add(new Example(heading, e.pairs(), e.image()));
            }
        }

        String module = blank(r.getValidationModule()) ? DEFAULT_MODULE : clean(r.getValidationModule());
        String server = blank(r.getValidationServer()) ? DEFAULT_SERVER : clean(r.getValidationServer());
        List<Step> steps = resolveSteps(r.getValidationSteps(), module, server, stepImage);

        List<String> legacyImages = new ArrayList<>();
        List<String> rawImages = nonEmpty(r.getRequerimientosImagenes()) ? r.getRequerimientosImagenes()
                : (blank(r.getRequerimientosImagen()) ? List.of() : List.of(r.getRequerimientosImagen()));
        for (String img : rawImages) {
            String safe = HtmlSanitizer.safeImageDataUri(img);
            if (!safe.isEmpty()) legacyImages.add(safe);
        }

        List<UatBlock> blocks = new ArrayList<>();
        if (r.getBlocks() != null) {
            for (UatBlock b : r.getBlocks()) {
                if (b != null && (!blank(b.getTexto()) || !HtmlSanitizer.safeImageDataUri(b.getImagenBase64()).isEmpty()))
                    blocks.add(b);
            }
        }

        // El texto legacy de requerimientos solo se usa si no hay introducción nueva
        String legacyReq = blank(r.getRequirementsIntro()) ? clean(r.getRequerimientos()) : "";

        String dateText = dateText(r.getPublishDate(), name);
        String closing = blank(r.getCierre()) ? DEFAULT_CLOSING : clean(r.getCierre());
        String subject = blank(r.getSubject()) ? subject(rfcLabel, name) : clean(r.getSubject());

        return new UatMailModel(greeting, rfcLabel, name, dateText, clean(r.getAdjunto()), related,
                clean(r.getRequirementsIntro()), legacyReq, legacyImages, reqs, examples, blocks,
                clean(r.getNota()), module, steps, closing, subject);
    }

    /** Asunto por defecto: "VoBo UAT – RFC {n} – {nombre}". */
    static String subject(String rfcLabel, String name) {
        StringBuilder sb = new StringBuilder("VoBo UAT");
        if (!rfcLabel.isEmpty()) sb.append(" \u2013 ").append(rfcLabel);
        if (!name.isEmpty()) sb.append(" \u2013 ").append(name);
        return sb.toString();
    }

    /** Normaliza "25473", "rfc 25473" o "RFC25473" a "RFC 25473". */
    static String rfcLabel(String raw) {
        String c = clean(raw).replaceFirst("(?i)^rfc\\s*", "");
        return c.isEmpty() ? "" : "RFC " + c;
    }

    private static List<Step> resolveSteps(List<UatRequest.Step> input, String module, String server,
                                           IntFunction<String> stepImage) {
        List<Step> out = new ArrayList<>();
        int n = 1;
        if (input != null) {
            for (UatRequest.Step s : input) {
                if (s == null) continue;
                ResolvedImage img = resolveStepImage(s.getImage(), stepImage);
                if (blank(s.getText()) && img.uri().isEmpty()) continue;
                out.add(new Step(n++, clean(s.getText()), img.uri(), img.maxWidth()));
            }
        }
        if (!out.isEmpty()) return out;
        String[] texts = {"Entrar a la carpeta M\u00f3dulos SFYC UAT", "Ejecutar el m\u00f3dulo " + module,
                "En el campo Servidor seleccionar " + server};
        for (int i = 0; i < texts.length; i++) {
            ResolvedImage img = resolveStepImage("@step" + (i + 1), stepImage);
            out.add(new Step(i + 1, texts[i], img.uri(), img.maxWidth()));
        }
        return out;
    }

    private record ResolvedImage(String uri, int maxWidth) {}

    private static ResolvedImage resolveStepImage(String raw, IntFunction<String> stepImage) {
        if (blank(raw)) return new ResolvedImage("", 420);
        java.util.regex.Matcher m = STEP_TOKEN.matcher(raw.trim());
        if (m.matches()) {
            int idx = Integer.parseInt(m.group(1));
            String uri = stepImage == null ? null : stepImage.apply(idx);
            return new ResolvedImage(uri == null ? "" : HtmlSanitizer.safeImageDataUri(uri), idx == 1 ? 420 : 260);
        }
        return new ResolvedImage(HtmlSanitizer.safeImageDataUri(raw), 420);
    }

    /** Fecha legible, o null si está vacía o el nombre del cambio ya la incluye. */
    static String dateText(String raw, String name) {
        String d = clean(raw);
        if (d.isEmpty()) return null;
        String normName = fold(name);
        java.util.regex.Matcher m = ISO_DATE.matcher(d);
        if (m.matches()) {
            int year = Integer.parseInt(m.group(1)), month = Integer.parseInt(m.group(2)), day = Integer.parseInt(m.group(3));
            LocalDate date;
            try { date = LocalDate.of(year, month, day); } catch (RuntimeException ex) { return d; }
            String monthName = MONTHS[date.getMonthValue() - 1];
            Pattern dayMonth = Pattern.compile("\\b0?" + day + "\\b[^\\d]{0,8}" + monthName);
            String slash = String.format(Locale.ROOT, "%02d/%02d/%04d", day, month, year);
            if (dayMonth.matcher(normName).find() || normName.contains(slash) || normName.contains(d)) return null;
            return String.format(Locale.ROOT, "%02d de %s de %d", day, monthName, year);
        }
        return normName.contains(fold(d)) ? null : d;
    }

    private static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static List<String> cleanList(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in != null) for (String s : in) if (!blank(s)) out.add(s.trim());
        return out;
    }

    private static boolean nonEmpty(List<?> l) { return l != null && !l.isEmpty(); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String clean(String s) { return blank(s) ? "" : s.trim(); }
}
