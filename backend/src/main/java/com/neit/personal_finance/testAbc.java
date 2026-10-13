package com.neit.personal_finance;

import com.neit.personal_finance.entities.Budget;
import com.neit.personal_finance.entities.Node;
import com.neit.personal_finance.entities.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class testAbc {

    public static void main(String[] args) {
        List<Integer> intList = Arrays.asList(1, 2, 3);
        List<String> strList = Arrays.asList("A", "B", "C");

        // Hợp lệ! Hàm nhận List<?> chấp nhận cả hai kiểu danh sách
        printList(intList);
        printList(strList);
    }

    public static void printList(List<?> list) {
        for (Object element : list) {
            System.out.println(element); // Trình biên dịch hiểu phần tử lấy ra luôn là một Object
        }
    }


}
