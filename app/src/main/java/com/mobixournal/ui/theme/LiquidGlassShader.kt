package com.mobixournal.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.lang.annotations.Language
import kotlin.math.PI

/**
 * CompositionLocal providing whether hardware-accelerated AGSL Liquid Glass shaders
 * are enabled (on Android 13+ / API 33+). Can be toggled in App Settings.
 */
val LocalLiquidGlassShaders = compositionLocalOf { true }

@Language("AGSL")
private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

@Language("AGSL")
private const val LIQUID_GLASS_SPECULAR_SHADER = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 rimColorTop;
layout(color) uniform half4 rimColorBottom;
layout(color) uniform half4 specularColor;
uniform float angle;
uniform float falloff;
uniform float rimWidth;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    float distToEdge = abs(sd + rimWidth * 0.5);
    if (distToEdge > rimWidth * 1.6) {
        return half4(0.0);
    }
    
    float vertT = clamp(coord.y / max(1.0, size.y), 0.0, 1.0);
    half4 baseColor = mix(rimColorTop, rimColorBottom, vertT);
    
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 lightDir = float2(cos(angle), sin(angle));
    float d = dot(grad, lightDir);
    float specular = pow(max(0.0, d), falloff);
    
    float alpha = smoothstep(rimWidth * 1.6, rimWidth * 0.2, distToEdge);
    half4 finalColor = mix(baseColor, specularColor, specular * 0.85);
    return finalColor * alpha;
}
"""

@Language("AGSL")
private const val LIQUID_GLASS_REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(max(0.0, 1.0 - x * x));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

/**
 * Creates and safely caches AGSL RuntimeShader instances on API 33+.
 */
object LiquidGlassShaderFactory {
    private var specularShader: RuntimeShader? = null
    private var refractionShader: RuntimeShader? = null
    private var initAttempted = false

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getSpecularShader(): RuntimeShader? {
        if (!initAttempted) {
            initAttempted = true
            try {
                specularShader = RuntimeShader(LIQUID_GLASS_SPECULAR_SHADER)
                refractionShader = RuntimeShader(LIQUID_GLASS_REFRACTION_SHADER)
            } catch (_: Throwable) {
                specularShader = null
                refractionShader = null
            }
        }
        return specularShader
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getRefractionShader(): RuntimeShader? {
        if (!initAttempted) getSpecularShader()
        return refractionShader
    }
}

/**
 * Enhanced rim modifier that applies AGSL specular curvature illumination on API 33+,
 * or gracefully degrades to the standard gradient stroke on older Android versions or when disabled.
 */
@Composable
fun Modifier.liquidGlassSpecularRim(
    cornerRadius: Dp = ModernTheme.panelCornerRadiusDp,
    rim: Color = rememberMenuRimColor(),
    enabled: Boolean = LocalLiquidGlassShaders.current,
): Modifier {
    if (!enabled || !LiquidGlassShaderFactory.isSupported) {
        return this.drawWithContent {
            drawContent()
            drawFloatingRim(cornerRadius.toPx(), rim)
        }
    }

    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val rimWidthPx = with(density) { MENU_RIM_WIDTH.toPx() }

    return this.drawWithCache {
        val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LiquidGlassShaderFactory.getSpecularShader()
        } else null

        if (shader == null) {
            onDrawWithContent {
                drawContent()
                drawFloatingRim(cornerRadiusPx, rim)
            }
        } else {
            val w = size.width
            val h = size.height
            shader.setFloatUniform("size", w, h)
            shader.setFloatUniform("cornerRadii", cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx)
            shader.setColorUniform(
                "rimColorTop",
                android.graphics.Color.valueOf(rim.red, rim.green, rim.blue, MENU_RIM_ALPHA_TOP),
            )
            shader.setColorUniform(
                "rimColorBottom",
                android.graphics.Color.valueOf(rim.red, rim.green, rim.blue, MENU_RIM_ALPHA_BOTTOM),
            )
            shader.setColorUniform(
                "specularColor",
                android.graphics.Color.valueOf(1f, 1f, 1f, 0.95f),
            )
            // Light source angled at -45 degrees (top-left)
            shader.setFloatUniform("angle", (-PI * 0.25).toFloat())
            shader.setFloatUniform("falloff", 3.0f)
            shader.setFloatUniform("rimWidth", rimWidthPx)

            val brush = ShaderBrush(shader)

            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = brush,
                    topLeft = Offset.Zero,
                    size = size,
                )
            }
        }
    }
}

/**
 * Optical lens refraction around the perimeter of a floating panel on API 33+.
 * Leaves the central contents completely crisp and un-distorted.
 */
@Composable
fun Modifier.liquidGlassLensBevel(
    cornerRadius: Dp = ModernTheme.panelCornerRadiusDp,
    enabled: Boolean = LocalLiquidGlassShaders.current,
): Modifier {
    if (!enabled || !LiquidGlassShaderFactory.isSupported) return this

    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val refractionHeightPx = with(density) { 14.dp.toPx() }
    val refractionAmountPx = with(density) { 7.dp.toPx() }

    return this.graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = LiquidGlassShaderFactory.getRefractionShader()
            if (shader != null && size.width > 0f && size.height > 0f) {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("cornerRadii", cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx)
                shader.setFloatUniform("refractionHeight", refractionHeightPx)
                shader.setFloatUniform("refractionAmount", refractionAmountPx)
                shader.setFloatUniform("depthEffect", 0.25f)

                try {
                    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                } catch (_: Throwable) {
                    renderEffect = null
                }
            }
        }
    }
}
