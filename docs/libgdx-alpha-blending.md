# libGDX Alpha(透明度)经验总结

> 本文档记录本项目在实现"回合开场过渡遮罩"时遇到的一个典型 libGDX
> 半透明渲染 bug:半透明元素被渲染成完全不透明。记录根因、识别特征与
> 修复方式,作为后续开发的参考。

## 1. 事件回顾

为游戏增加回合开场的过渡动画时,需要绘制一个半透明黑色遮罩:

```java
shape.setColor(0f, 0f, 0f, 0.5f);   // 50% 不透明度
shape.rect(0f, 0f, WORLD_W, WORLD_H);
```

预期:画面压暗 50%,可透出底层场景。
实际:**整屏纯黑**,直到 3 秒动画结束瞬间才恢复正常画面。

即使把 alpha 降到 0.22,画面仍然"近乎完全黑屏"——alpha 完全没生效。

## 2. 根因:混合状态(Blending)在跨绘制 pass 时丢失

### 2.1 关键事实(经 libGDX 1.14.2 字节码反编译确认)

| 方法 | 对 `GL_BLEND` 的行为 |
|---|---|
| `SpriteBatch.begin()` | **开启**混合(若 blendingEnabled,默认开启) |
| `SpriteBatch.end()` | **显式 `glDisable(GL_BLEND)`** |
| `ShapeRenderer.begin()` | **不触碰**混合状态 |

### 2.2 触发条件

```java
drawHud();          // batch.end() 执行后,GL_BLEND 已被关闭
drawRoundBanner();  // 此时用 ShapeRenderer 绘制半透明 → alpha 被忽略
```

即:**在 `SpriteBatch` 结束之后、紧接着用 `ShapeRenderer` 绘制半透明图形**。

### 2.3 为什么 alpha 会被忽略

`GL_BLEND` 关闭时,OpenGL 固定管线直接写入片段颜色,**alpha 通道不参与
任何混合运算**。`setColor(0,0,0,0.5)` 与 `setColor(0,0,0,1)` 渲染结果
完全一样——都是不透明纯黑。

### 2.4 为什么其他半透明元素一直正常

`render()` 开头有显式重开混合:

```java
Gdx.gl.glEnable(GL20.GL_BLEND);
Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
```

阴影、枪口闪光、护盾、游戏结束压暗等半透明元素都在主绘制流程内执行,
混合处于开启状态,因此从未触发该问题。只有被放到 `drawHud()` 之后的
横幅绘制踩中了这个坑。

## 3. 识别特征

- 半透明图形变成完全不透明,且**透明度数值无论如何调整都无变化**
  (0.22 和 0.5 看起来一样黑)
- 症状出现在**跨 pass 的绘制顺序**上:先 batch 后 shape,或先 shape 后 batch
- 相同绘制代码在别处(主流程内)正常,换个位置就异常

## 4. 修复方式

在每次开始 `ShapeRenderer` 绘制半透明内容前,主动确保混合状态正确
(与 `render()` 开头的防御性写法一致):

```java
Gdx.gl.glEnable(GL20.GL_BLEND);
Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
shape.begin(ShapeRenderer.ShapeType.Filled);
```

## 5. Alpha 使用注意事项(最佳实践)

1. **混合状态不是持久的**。`SpriteBatch.end()` 会关闭混合;
   `ShapeRenderer` 不会主动管理它。跨 pass 前务必重新启用。
2. **在关键绘制阶段前显式设置混合**。libGDX 在部分后端上,`create()`
   里设置一次的 GL 状态可能在首帧前丢失,因此 `render()` 每帧重设是
   稳妥做法(本项目 `render()` 已有此模式)。
3. **统一混合函数**。使用标准 `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA`;
   `SpriteBatch` 默认即此配置,shape 与其保持一致可避免颜色差异。
4. **注意 premultiplied alpha 陷阱**。若纹理/渲染器使用预乘 alpha,
   混合函数需相应改为 `GL_ONE, GL_ONE_MINUS_SRC_ALPHA`,否则边缘会出现
   黑边或发暗。本项目未使用预乘,保持标准混合即可。
5. **调试排查思路**:
   - 半透明变全实 → 先怀疑 `GL_BLEND` 是否开启
   - 检查该绘制调用之前最近的 `batch.end()` / `shape.end()`
   - 可在可疑位置前临时加 `Gdx.gl.glIsEnabled(GL20.GL_BLEND)` 打印确认
6. **防御性编码**:把"启用混合 + 设置混合函数"封装为小工具方法,
   在每次 shape 绘制半透明前调用,避免重复踩坑。

## 6. 相关代码位置

- 回合横幅过渡:`GameWorld#drawRoundBanner`(遮罩、文字、点缀线)
- 主流程混合重设:`GameWorld#render` 开头
