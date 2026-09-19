package com.create.productionline.util;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.line.scheme.LineScheme;

/**
 * Renders a plan as ONE flowing topology chain:
 *
 * <pre>[base] -> Machine[material] -> Machine[material] -> … -> product</pre>
 *
 * <p>The base material is the head of the chain (the feed pseudo-step is not shown),
 * every real station carries the material(s) it consumes in brackets, and the product
 * closes the chain. Long chains are wrapped by {@link TextWrap} with an indent so they
 * never overflow the tooltip.
 */
public final class SchemeTopology {

    /** Pseudo facility of the "material enters the line" step — rendered as the head, not a station. */
    private static final String FEED = "cpl:feed";

    private SchemeTopology() {
    }

    /** One real station: a facility plus the materials it consumes. */
    public record Station(String facility, List<String> inputs) {
    }

    public static List<String> lines(LineScheme scheme) {
        if (scheme == null) {
            return List.of();
        }
        List<Station> stations = new ArrayList<>();
        for (LineScheme.Step step : scheme.getSteps()) {
            if (FEED.equals(step.getFacilityType())) {
                continue; // the base is the head of the chain
            }
            stations.add(new Station(step.getFacilityType(), new ArrayList<>(step.getInputs())));
        }
        List<String> out = new ArrayList<>(lines(scheme.getBaseMaterial(), stations, scheme.getOutputItem()));
        // The repeat instruction closes the chain: one pass through the stations above
        // yields one craft, so a bigger target means running the line again.
        if (scheme.repeats()) {
            boolean loop = scheme.recyclesProduct();
            // Every wrapped fragment has to be kept: the instruction is long enough to be
            // split, and keeping only the first fragment silently truncates the part that
            // explains HOW to repeat.
            out.addAll(TextWrap.wrapIndented((loop ? "loop " : "repeat ") + scheme.getRepeatCount() + "x -> "
                    + scheme.getTargetOutputCount() + " " + Names.nameOfItem(scheme.getOutputItem())
                    + (loop ? " (feed the product back to the belt head)"
                            : " (re-feed the base each pass)"), "  "));
        }
        return out;
    }

    /** Wrapped topology lines; empty when there is nothing to show. */
    public static List<String> lines(String baseMaterial, List<Station> stations, String outputItem) {
        String chain = chain(baseMaterial, stations, outputItem);
        return chain.isBlank() ? List.of() : TextWrap.wrapIndented(chain, "  ");
    }

    /** The raw, unwrapped chain string (used by tests/debug). */
    public static String chain(String baseMaterial, List<Station> stations, String outputItem) {
        List<String> parts = new ArrayList<>();
        String base = baseMaterial == null ? "" : baseMaterial.trim();
        if (!base.isBlank()) {
            parts.add("[" + Names.cap(Names.nameOfItem(base)) + "]");
        }
        if (stations != null) {
            for (Station station : stations) {
                StringBuilder sb = new StringBuilder(
                        Names.cap(Names.facilityName(station.facility())));
                List<String> shown = new ArrayList<>();
                if (station.inputs() != null) {
                    for (String in : station.inputs()) {
                        if (in == null || in.isBlank() || in.equals(base)) {
                            continue; // never repeat the head material
                        }
                        String name = Names.cap(Names.nameOfItem(in));
                        if (!shown.contains(name)) {
                            shown.add(name);
                        }
                    }
                }
                if (!shown.isEmpty()) {
                    sb.append('[').append(String.join(",", shown)).append(']');
                }
                parts.add(sb.toString());
            }
        }
        String out = outputItem == null ? "" : outputItem.trim();
        if (!out.isBlank()) {
            parts.add(Names.cap(Names.nameOfItem(out)));
        }
        return String.join(" -> ", parts);
    }
}
