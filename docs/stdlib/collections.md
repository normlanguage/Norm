# 集合设计

Norm 集合是类型化值容器。语言保证普通赋值后两处修改互不影响，运行时可以使用写时复制或持久化结构优化。

```norm
List<int> first = List<int>(values = [1, 2, 3])
List<int> second = first
second.add(value = 4)
```

执行后 first 的内容不变。需要共享时必须显式使用 `Ref<List<int>>`。

## 类型

- Array：固定长度、可按索引更新；
- List：可增长的有序序列；
- Map：唯一键到值的映射；
- Set：按 equality 与 hash 去重的值集合。

所有泛型实参都必填，`List`、`Map` 这类 raw type 非法。

## 缺失与越界

Map 查找和可能找不到的集合操作返回 Option；List/Array 索引越界属于程序错误并产生 IndexError。API 不用 null 同时表达“没有元素”和“元素值为 null”。

## 迭代

集合实现统一迭代协议。通用 Map 和 Set 不承诺顺序，需要稳定顺序时使用专门类型或显式排序。迭代期间结构修改会使迭代器失效。

详细函数签名见[Collections API](/stdlib/collections-api)。

