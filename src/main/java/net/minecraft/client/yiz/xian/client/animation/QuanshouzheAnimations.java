package net.minecraft.client.yiz.xian.client.animation;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public final class QuanshouzheAnimations {

    private QuanshouzheAnimations() {}

    public static final AnimationDefinition QI_DAN = AnimationDefinition.Builder.withLength(1.7917F)
        .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.125F, KeyframeAnimations.degreeVec(-30.0F, -10.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-70.0F, -15.0F, 20.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.4583F, KeyframeAnimations.degreeVec(-110.0F, -25.0F, 18.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(-197.9599F, -17.6606F, 61.0145F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.125F, KeyframeAnimations.degreeVec(-30.0F, 10.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-70.0F, 15.0F, -20.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.4583F, KeyframeAnimations.degreeVec(-110.0F, 25.0F, -18.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(-186.7789F, 1.2621F, -39.1407F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("bone2", new AnimationChannel(AnimationChannel.Targets.POSITION,
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.posVec(0.0F, 4.45F, 0.14F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.4167F, KeyframeAnimations.posVec(0.0F, 11.54F, 1.27F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.posVec(0.0F, 14.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 33.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("bone2", new AnimationChannel(AnimationChannel.Targets.SCALE,
            new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.scaleVec(1.6364F, 1.3364F, 1.2364F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.4167F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.scaleVec(3.0F, 3.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.0F, KeyframeAnimations.scaleVec(9.0F, 9.0F, 9.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();

    public static final AnimationDefinition QI_DAN_2 = AnimationDefinition.Builder.withLength(0.5F).looping()
        .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0417F, KeyframeAnimations.degreeVec(-197.9599F, -17.6606F, 61.0145F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(-110.0F, -2.0F, 40.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(-58.0541F, -19.1967F, 29.5674F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0417F, KeyframeAnimations.degreeVec(-186.7789F, 1.2621F, -39.1407F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(-110.0F, 2.0F, -40.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-58.0541F, 19.1967F, -29.5674F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("bone2", new AnimationChannel(AnimationChannel.Targets.POSITION,
            new Keyframe(0.0417F, KeyframeAnimations.posVec(0.0F, 33.0F, -15.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.posVec(0.0F, 45.0F, -76.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2917F, KeyframeAnimations.posVec(0.0F, 35.75F, -138.5F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -34.0F, -212.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("bone2", new AnimationChannel(AnimationChannel.Targets.SCALE,
            new Keyframe(0.0417F, KeyframeAnimations.scaleVec(3.0F, 3.0F, 3.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();

    public static final AnimationDefinition QI_DAN_3 = AnimationDefinition.Builder.withLength(2.0833F)
        .addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(-30.0F, -10.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.375F, KeyframeAnimations.degreeVec(-70.0F, -15.0F, 20.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5417F, KeyframeAnimations.degreeVec(-110.0F, -25.0F, 18.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.625F, KeyframeAnimations.degreeVec(-197.9599F, -17.6606F, 61.0145F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2917F, KeyframeAnimations.degreeVec(-225.4599F, -17.6606F, 61.0145F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5F, KeyframeAnimations.degreeVec(-110.0F, -2.0F, 40.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0417F, KeyframeAnimations.degreeVec(-58.0541F, -19.1967F, 29.5674F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(-30.0F, 10.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.375F, KeyframeAnimations.degreeVec(-70.0F, 15.0F, -20.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5417F, KeyframeAnimations.degreeVec(-110.0F, 25.0F, -18.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.625F, KeyframeAnimations.degreeVec(-186.7789F, 1.2621F, -39.1407F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2917F, KeyframeAnimations.degreeVec(-214.2789F, 1.2621F, -39.1407F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5F, KeyframeAnimations.degreeVec(-110.0F, 2.0F, -40.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0417F, KeyframeAnimations.degreeVec(-58.0541F, 19.1967F, -29.5674F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("bone2", new AnimationChannel(AnimationChannel.Targets.POSITION,
            new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2917F, KeyframeAnimations.posVec(0.0F, 4.45F, 0.14F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.625F, KeyframeAnimations.posVec(0.0F, 11.54F, 1.27F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.9167F, KeyframeAnimations.posVec(0.0F, 14.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2917F, KeyframeAnimations.posVec(0.0F, 33.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 7.0F, -51.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.7083F, KeyframeAnimations.posVec(0.0F, -34.0F, -86.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0417F, KeyframeAnimations.posVec(0.0F, -71.0F, -121.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("bone2", new AnimationChannel(AnimationChannel.Targets.SCALE,
            new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2917F, KeyframeAnimations.scaleVec(1.6364F, 1.3364F, 1.2364F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.4167F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.9167F, KeyframeAnimations.scaleVec(3.0F, 3.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2917F, KeyframeAnimations.scaleVec(9.0F, 9.0F, 9.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();
}
