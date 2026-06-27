package com.example.ownphotoonwall;

import android.graphics.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SnakeEngine {
    public List<Point> snake = new ArrayList<>();
    public Point food;
    public int width = 15, height = 15; // Grid size

    public SnakeEngine() {
        // Start snake in the middle
        snake.add(new Point(5, 5));
        spawnFood();
    }

    public void move() {
        Point head = snake.get(0);
        int nextX = head.x;
        int nextY = head.y;

        // Autopilot Algorithm
        if (head.x != food.x) {
            nextX += (food.x > head.x) ? 1 : -1;
        } else if (head.y != food.y) {
            nextY += (food.y > head.y) ? 1 : -1;
        }

        Point newHead = new Point(nextX, nextY);

        // Add the new head to move forward
        snake.add(0, newHead);

        // Check if it ate the food
        if (newHead.equals(food)) {
            spawnFood();
        }

        // THE FIX: Always trim the tail if the snake gets longer than 4 blocks!
        // This ensures it never grows infinitely.
        while (snake.size() > 4) {
            snake.remove(snake.size() - 1);
        }
    }

    public void spawnFood() {
        Random r = new Random();
        food = new Point(r.nextInt(width), r.nextInt(height));
    }
}