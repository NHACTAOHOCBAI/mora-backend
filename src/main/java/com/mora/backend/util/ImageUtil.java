package com.mora.backend.util;

import lombok.extern.slf4j.Slf4j;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

@Slf4j
public class ImageUtil {

    private static final int MAX_DIMENSION = 1024;
    private static final float JPEG_QUALITY = 0.8f;

    public static byte[] resizeAndCompress(BufferedImage originalImage) {
        if (originalImage == null) {
            return new byte[0];
        }

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        BufferedImage targetImage = originalImage;

        // 1. Resize if dimensions exceed limit
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
            int targetWidth = (int) (width * scale);
            int targetHeight = (int) (height * scale);

            Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            targetImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = targetImage.createGraphics();
            // Vẽ nền trắng để tránh lỗi đen nền khi chuyển từ PNG trong suốt sang JPEG
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, targetWidth, targetHeight);
            g2d.drawImage(resultingImage, 0, 0, null);
            g2d.dispose();
        } else if (originalImage.getType() != BufferedImage.TYPE_INT_RGB) {
            // Chuyển đổi sang RGB với nền trắng nếu ảnh ban đầu có độ trong suốt (như PNG)
            targetImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = targetImage.createGraphics();
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, width, height);
            g2d.drawImage(originalImage, 0, 0, null);
            g2d.dispose();
        }

        // 2. Compress to JPEG with 80% quality
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("Không tìm thấy ImageWriter cho JPG");
            }
            ImageWriter writer = writers.next();

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionType(param.getCompressionTypes()[0]);
                    param.setCompressionQuality(JPEG_QUALITY);
                }

                writer.write(null, new IIOImage(targetImage, null, null), param);
            } finally {
                writer.dispose();
            }
        } catch (IOException e) {
            log.error("Lỗi khi nén ảnh JPEG", e);
            // Fallback: Lưu không nén
            try {
                baos.reset();
                ImageIO.write(targetImage, "jpg", baos);
            } catch (IOException ex) {
                log.error("Fallback ghi ảnh cũng thất bại", ex);
            }
        }

        return baos.toByteArray();
    }

    public static byte[] resizeAndCompress(byte[] rawImageBytes) {
        if (rawImageBytes == null || rawImageBytes.length == 0) {
            return new byte[0];
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(rawImageBytes)) {
            BufferedImage image = ImageIO.read(bais);
            if (image == null) {
                log.warn("Không thể đọc ảnh từ dữ liệu nhị phân");
                return rawImageBytes;
            }
            return resizeAndCompress(image);
        } catch (Exception e) {
            log.error("Lỗi khi xử lý dữ liệu ảnh nhị phân", e);
            return rawImageBytes;
        }
    }
}
