package com.github.cfogrady.vb.dim.card;

import com.github.cfogrady.vb.dim.sprite.SpriteData;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Assertions;

import java.io.File;

/** Local card images remain outside version control; explicitly configured paths must exist. */
final class CardTestFixtures {
    static File dim() { return image("VBHELPER_TEST_DIM", "original.bin"); }
    static File bem() { return image("VBHELPER_TEST_BEM", "BEM_CARD_IMAGE.bin"); }

    private static File image(String variable, String fallback) {
        String configured = System.getenv(variable);
        File file = new File(configured == null ? fallback : configured);
        if (configured != null) {
            Assertions.assertTrue(file.isFile(), variable + " points to a missing file");
        } else {
            Assumptions.assumeTrue(file.isFile(), "Optional card fixture missing; set " + variable);
        }
        return file;
    }

    static SpriteData.Sprite changedSprite(SpriteData.Sprite original) {
        byte[] pixels = original.getPixelData().clone();
        Assertions.assertTrue(pixels.length > 0);
        pixels[0] ^= 1;
        return SpriteData.Sprite.builder().width(original.getWidth()).height(original.getHeight())
                .pixelData(pixels).build();
    }
}
