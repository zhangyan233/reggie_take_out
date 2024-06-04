package com.itheima.reggie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.mapper.OrderMapper;
import com.itheima.reggie.pojo.OrderDetail;
import com.itheima.reggie.pojo.Orders;
import com.itheima.reggie.pojo.ShoppingCart;
import com.itheima.reggie.pojo.User;
import com.itheima.reggie.service.*;
import com.itheima.reggie.pojo.AddressBook;
import com.itheima.reggie.utils.BaseContext;
import com.itheima.reggie.utils.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements OrderService {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private UserService userService;

    @Autowired
    private AddressBookService addressBookService;

    @Autowired
    private OrderDetailService orderDetailService;


    /**
     * 用户下单
     * @param orders
     */
    @Transactional
    public void submit(Orders orders) {
        //获取当前用户id
        Long currentId = BaseContext.getCurrentId();

        //查询当前用户的购物车数据
        LambdaQueryWrapper<ShoppingCart> lqw=new LambdaQueryWrapper<>();
        lqw.eq(ShoppingCart::getUserId,currentId);
        List<ShoppingCart> shoppingCartList = shoppingCartService.list(lqw);

        if(shoppingCartList==null||shoppingCartList.size()==0){
            throw new CustomException("购物车为空，不能下单");
        }

        //查询用户和地址信息
        User user = userService.getById(currentId);
        Long addressBookId = orders.getAddressBookId();
        AddressBook addressBook = addressBookService.getById(addressBookId);
        if(addressBook==null){
            throw new CustomException("用户地址信息有误，无法下单");
        }

        //订单号
        long orderId = IdWorker.getId();

        //总金额
        AtomicInteger amount=new AtomicInteger(0);//保证多线程安全

        //遍历购物车数据
        List<OrderDetail> collect = shoppingCartList.stream().map((item) -> {
            OrderDetail orderDetail=new OrderDetail();
            orderDetail.setOrderId(orderId);
            orderDetail.setNumber(item.getNumber());
            orderDetail.setDishFlavor(item.getDishFlavor());
            orderDetail.setDishId(item.getDishId());
            orderDetail.setSetmealId(item.getSetmealId());
            orderDetail.setName(item.getName());
            orderDetail.setImage(item.getImage());
            orderDetail.setAmount(item.getAmount());
            amount.addAndGet(item.getAmount().multiply(new BigDecimal(item.getNumber())).intValue());
            return orderDetail;
        }).collect(Collectors.toList());

        //向order插入数据
        orders.setId(orderId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setCheckoutTime(LocalDateTime.now());
        orders.setStatus(2);
        orders.setAmount(new BigDecimal(amount.get()));//总金额
        orders.setUserId(currentId);
        orders.setNumber(String.valueOf(orderId));
        orders.setUserName(user.getName());
        orders.setConsignee(addressBook.getConsignee());
        orders.setPhone(addressBook.getPhone());
        orders.setAddress((addressBook.getProvinceName() == null ? "" : addressBook.getProvinceName())
                + (addressBook.getCityName() == null ? "" : addressBook.getCityName())
                + (addressBook.getDistrictName() == null ? "" : addressBook.getDistrictName())
                + (addressBook.getDetail() == null ? "" : addressBook.getDetail()));
        this.save(orders);

        //向orderDetail插入数据
        orderDetailService.saveBatch(collect);

        //清空购物车数据
        shoppingCartService.remove(lqw);

    }
}
