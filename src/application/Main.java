package application;

import application.graphics.Application;

import java.io.IOException;

public class Main {

    public static void main(String[] args){
        try {
            Application app = new Application();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //TODO hand port to application?
    }
}
