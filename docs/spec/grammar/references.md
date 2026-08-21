# Ref 引用语法

`Ref<T>` 是 Norm 中唯一显式表示共享 identity 的类型构造。它不是普通 nullable 引用，也不会由 class 赋值隐式产生。

```norm
class Counter {
    int value
}

Counter counter = Counter(value = 0)
Ref<Counter> shared = counter.ref()
```

## 形成与传播

`.ref()` 在一个 class 值上建立共享单元。之后复制 `Ref<T>` 会复制对该单元的引用：

```norm
Ref<Counter> left = Counter(value = 0).ref()
Ref<Counter> right = left
right.value = 1
// left.value == 1
```

普通 `Counter second = first` 仍按 class 的值语义复制，不会形成共享。

## 类型限制

- `T` 必须是 class 类型；`Ref<value>` 与 `Ref<interface>` 不是合法的直接构造。
- `Ref<T>` 自身不可 nullable，`Ref<T>?` 非法。
- `Ref<T?>` 非法，因为共享单元不能保存空 identity。
- 从 `Ref<T>` 读取方法和字段使用普通点号，不提供隐式解引用运算符。

需要表达“可能不存在的共享对象”时，应使用显式 enum：

```norm
enum SharedCounter {
    Missing
    Present(Ref<Counter> value)
}
```

`Ref<T>` 的线程安全性不由类型本身保证；并发访问需要标准库中的同步原语。

