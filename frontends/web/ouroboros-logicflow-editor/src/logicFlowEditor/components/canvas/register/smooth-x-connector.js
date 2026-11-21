import { Graph, Path } from '@antv/x6'

// 自定义连接器
Graph.registerConnector(
  'smooth-x',
  (origin, target) => {
    const offset = 6
    const deltaX = Math.abs(target.x - origin.x) // 两节点x轴距离
    const control = Math.floor((deltaX / 3) * 2) // 控制点位置

    const v1 = { x: origin.x + offset + control, y: origin.y } // 三次贝塞尔控制点1
    const v2 = { x: target.x - offset - control, y: target.y } // 三次贝塞尔控制点2

    return Path.normalize(
      `M ${origin.x} ${origin.y} L ${origin.x + offset} ${origin.y} C ${v1.x} ${v1.y} ${
        v2.x
      } ${v2.y} ${target.x - offset} ${target.y} L ${target.x} ${target.y}`
    )
  },
  true
)
