package com.example.aiagent;

import com.example.aiagent.tools.FileOperationTool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Assertions;

@SpringBootTest
public class FileOperationToolTest {

    @Test
    public void testReadFile(){
        FileOperationTool tool = new FileOperationTool();
        String fileName = "编程导航.txt";
        String result = tool.readFile(fileName);
        Assertions.assertNotNull(result);
        System.out.println(result);
    }

    @Test
    public void testWriteFile(){
        FileOperationTool tool = new FileOperationTool();
        String fileName = "编程导航.txt";
        String content = "dasfasfdafasfaf";
        String result =tool.writeFile(fileName,content);
        Assertions.assertNotNull(result);
        System.out.println(result);
    }
}
