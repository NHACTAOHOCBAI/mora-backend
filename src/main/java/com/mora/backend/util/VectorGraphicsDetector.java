package com.mora.backend.util;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.IOException;
import java.util.List;

public class VectorGraphicsDetector extends PDFStreamEngine {
    private int pathCount = 0;
    private final int pathThreshold;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VectorGraphicsDetector.class);

    public VectorGraphicsDetector() {
        this(30);
    }

    public VectorGraphicsDetector(Integer threshold) {
        super();
        this.pathThreshold = threshold != null ? threshold : 30;
    }

    public boolean detect(PDPage page, int pageNumber) {
        try {
            this.pathCount = 0;
            processPage(page);
            log.info("[VECTOR-DETECTOR] Page: {}, Total vector path count: {}", pageNumber, this.pathCount);
        } catch (IOException e) {
            log.warn("[VECTOR-DETECTOR] Failed to process page {}: {}", pageNumber, e.getMessage());
        }
        return this.pathCount > this.pathThreshold;
    }

    public int getPathCount() {
        return this.pathCount;
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String name = operator.getName();
        // S: Stroke path
        // f, F, f*: Fill path
        // B, B*, b, b*: Fill and stroke path
        // sh: Shading fill
        if ("S".equals(name) || "f".equals(name) || "F".equals(name) || "f*".equals(name) 
                || "B".equals(name) || "B*".equals(name) || "b".equals(name) || "b*".equals(name)
                || "sh".equals(name)) {
            pathCount++;
        }
        super.processOperator(operator, operands);
    }
}
