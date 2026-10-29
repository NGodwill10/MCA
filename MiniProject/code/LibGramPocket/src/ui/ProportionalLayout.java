package ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.util.Arrays;

public final class ProportionalLayout implements LayoutManager {
    private final boolean horizontal;
    private final double[] proportions;

    public ProportionalLayout(boolean horizontal, double... proportions) {
        double sum = Arrays.stream(proportions).sum();
        if (proportions.length == 0 || Math.abs(sum - 1.0) > 0.0001) {
            throw new IllegalArgumentException("Layout proportions must add up to 1.");
        }
        this.horizontal = horizontal;
        this.proportions = proportions.clone();
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
    }

    @Override
    public void removeLayoutComponent(Component component) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return new Dimension(900, 700);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(0, 0);
    }

    @Override
    public void layoutContainer(Container parent) {
        Component[] components = parent.getComponents();
        if (components.length != proportions.length) {
            throw new IllegalStateException("Each proportional region needs one component.");
        }
        int available = horizontal ? parent.getWidth() : parent.getHeight();
        int start = 0;
        double cumulative = 0;
        for (int index = 0; index < components.length; index++) {
            cumulative += proportions[index];
            int end = index == components.length - 1 ? available : (int) Math.round(available * cumulative);
            if (horizontal) {
                components[index].setBounds(start, 0, end - start, parent.getHeight());
            } else {
                components[index].setBounds(0, start, parent.getWidth(), end - start);
            }
            start = end;
        }
    }
}