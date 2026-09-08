export const moduleSource = 'Module module() { return module(name: "app", version: 1) }\n';

export const agentTasks = [
  {
    id: 'add_feature',
    prompt: '为 app 添加 Integer shippingCost(Integer subtotal)。subtotal 小于 100 时返回 10，否则返回 0；保持 itemTotal 行为。输入为非负整数。',
    files: { 'api.norm': 'package app Integer itemTotal(Integer price, Integer count) { return price * count }\n' },
    acceptance: `package app
import std.testing.Test
@Test Void acceptance() {
  require(condition: shippingCost(subtotal: 0) == 10, message: "zero")
  require(condition: shippingCost(subtotal: 99) == 10, message: "below threshold")
  require(condition: shippingCost(subtotal: 100) == 0, message: "threshold")
  require(condition: shippingCost(subtotal: 101) == 0, message: "above threshold")
  require(condition: itemTotal(price: 7, count: 3) == 21, message: "existing behavior")
}
`,
  },
  {
    id: 'fix_type',
    prompt: '修复 doubled 的类型错误，使其返回输入整数的两倍，并保持函数签名。',
    files: { 'api.norm': 'package app Integer doubled(Integer value) { return "wrong" }\n' },
    acceptance: `package app
import std.testing.Test
@Test Void acceptance() {
  require(condition: doubled(value: -3) == -6, message: "negative")
  require(condition: doubled(value: 0) == 0, message: "zero")
  require(condition: doubled(value: 21) == 42, message: "positive")
}
`,
  },
  {
    id: 'rename',
    prompt: '将 total 语义重命名为 amount，更新所有调用与声明引用，保持 checkout 的行为；不保留 total 的兼容包装。',
    files: {
      'api.norm': 'package app Integer total(Integer value) { return value + 1 }\n',
      'checkout.norm': 'package app Integer checkout(Integer value) { return total(value: value) }\n',
      'tests/case.norm': 'package app import std.testing.Test @Test(functions: [total.function]) Void currentBehavior() { require(condition: total(value: 1) == 2, message: "total") }\n',
    },
    absentSymbols: ['total'],
    acceptance: `package app
import std.testing.Test
@Test Void acceptance() {
  require(condition: amount(value: 41) == 42, message: "renamed API")
  require(condition: checkout(value: 41) == 42, message: "updated caller")
  require(condition: amount(value: -1) == 0, message: "preserved behavior")
}
`,
  },
  {
    id: 'change_api',
    prompt: '为 amount 增加必填 Integer quantity 参数，返回 unitPrice * quantity。更新 checkout，使其以单价 7、数量 3 调用 amount。不保留旧重载或默认 quantity。',
    files: {
      'api.norm': 'package app Integer amount(Integer unitPrice) { return unitPrice }\n',
      'checkout.norm': 'package app Integer checkout() { return amount(unitPrice: 7) }\n',
    },
    acceptance: `package app
import std.testing.Test
@Test Void acceptance() {
  require(condition: amount(unitPrice: 5, quantity: 4) == 20, message: "quantity")
  require(condition: amount(unitPrice: 5, quantity: 0) == 0, message: "zero quantity")
  require(condition: checkout() == 21, message: "updated caller")
}
`,
    rejectedCall: 'package app Integer obsolete() { return amount(unitPrice: 7) }\n',
  },
  {
    id: 'add_tests',
    prompt: '为 clamp 添加可执行 @Test，覆盖负数、零和正数，关联 clamp 声明。保持生产源码不变。测试必须能够捕获未把负数归零的错误实现。',
    files: { 'api.norm': 'package app Integer clamp(Integer value) { if (value < 0) { return 0 } return value }\n' },
    acceptance: `package app
import std.testing.Test
@Test Void acceptance() {
  require(condition: clamp(value: -1) == 0, message: "negative")
  require(condition: clamp(value: 0) == 0, message: "zero")
  require(condition: clamp(value: 7) == 7, message: "positive")
}
`,
    mutants: [
      'package app Integer clamp(Integer value) { return value }\n',
      'package app Integer clamp(Integer value) { if (value == 0) { return 1 } if (value < 0) { return 0 } return value }\n',
      'package app Integer clamp(Integer value) { return 0 }\n',
    ],
  },
];
