-- ============================================================
-- 电商系统业务数据库
-- 字符集: utf8mb4 (完整中文支持)
-- 引擎: InnoDB (外键约束)
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 用户表
-- ----------------------------
DROP TABLE IF EXISTS `dt_user`;
CREATE TABLE `dt_user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(50) NOT NULL UNIQUE,
  `nickname` VARCHAR(50) NOT NULL,
  `phone` VARCHAR(20),
  `email` VARCHAR(100),
  `password_hash` VARCHAR(128) NOT NULL,
  `gender` TINYINT DEFAULT 0 COMMENT '0-未知 1-男 2-女',
  `avatar_url` VARCHAR(255),
  `status` TINYINT DEFAULT 1 COMMENT '0-禁用 1-正常',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_phone` (`phone`),
  INDEX `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- 2. 收货地址表
-- ----------------------------
DROP TABLE IF EXISTS `dt_user_address`;
CREATE TABLE `dt_user_address` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `receiver_name` VARCHAR(50) NOT NULL,
  `phone` VARCHAR(20) NOT NULL,
  `province` VARCHAR(50) NOT NULL,
  `city` VARCHAR(50) NOT NULL,
  `district` VARCHAR(50) NOT NULL,
  `detail_address` VARCHAR(255) NOT NULL,
  `is_default` TINYINT DEFAULT 0 COMMENT '0-否 1-是',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `dt_user`(`id`) ON DELETE CASCADE,
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

-- ----------------------------
-- 3. 商品分类表
-- ----------------------------
DROP TABLE IF EXISTS `dt_category`;
CREATE TABLE `dt_category` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID，0表示一级',
  `name` VARCHAR(100) NOT NULL,
  `icon_url` VARCHAR(255),
  `sort_order` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '0-禁用 1-启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- ----------------------------
-- 4. 商品表
-- ----------------------------
DROP TABLE IF EXISTS `dt_product`;
CREATE TABLE `dt_product` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `category_id` BIGINT NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `subtitle` VARCHAR(500),
  `main_image` VARCHAR(500),
  `images` JSON COMMENT '详情图片数组',
  `price` DECIMAL(10,2) NOT NULL,
  `original_price` DECIMAL(10,2),
  `cost_price` DECIMAL(10,2),
  `stock` INT NOT NULL DEFAULT 0,
  `sales` INT NOT NULL DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '0-下架 1-上架',
  `description` TEXT,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`category_id`) REFERENCES `dt_category`(`id`),
  INDEX `idx_category_id` (`category_id`),
  FULLTEXT INDEX `ft_name_subtitle` (`name`, `subtitle`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- ----------------------------
-- 5. 购物车表
-- ----------------------------
DROP TABLE IF EXISTS `dt_cart_item`;
CREATE TABLE `dt_cart_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `quantity` INT NOT NULL DEFAULT 1,
  `selected` TINYINT DEFAULT 1 COMMENT '0-未选中 1-选中',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `dt_user`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`product_id`) REFERENCES `dt_product`(`id`) ON DELETE CASCADE,
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- ----------------------------
-- 6. 订单表
-- ----------------------------
DROP TABLE IF EXISTS `dt_order`;
CREATE TABLE `dt_order` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_no` VARCHAR(64) NOT NULL UNIQUE COMMENT '订单号',
  `user_id` BIGINT NOT NULL,
  `address_id` BIGINT NOT NULL,
  `total_amount` DECIMAL(12,2) NOT NULL COMMENT '订单总金额',
  `discount_amount` DECIMAL(12,2) DEFAULT 0 COMMENT '优惠金额',
  `shipping_fee` DECIMAL(10,2) DEFAULT 0 COMMENT '运费',
  `pay_amount` DECIMAL(12,2) NOT NULL COMMENT '实付金额',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-待付款 1-待发货 2-待收货 3-已完成 4-已取消 5-退款中 6-已退款',
  `pay_type` TINYINT COMMENT '1-支付宝 2-微信 3-银行卡',
  `pay_time` DATETIME,
  `ship_time` DATETIME,
  `receive_time` DATETIME,
  `cancel_time` DATETIME,
  `remark` VARCHAR(500),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `dt_user`(`id`),
  FOREIGN KEY (`address_id`) REFERENCES `dt_user_address`(`id`),
  INDEX `idx_order_no` (`order_no`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ----------------------------
-- 7. 订单商品表
-- ----------------------------
DROP TABLE IF EXISTS `dt_order_item`;
CREATE TABLE `dt_order_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `product_name` VARCHAR(255) NOT NULL,
  `product_image` VARCHAR(500),
  `unit_price` DECIMAL(10,2) NOT NULL,
  `quantity` INT NOT NULL,
  `total_price` DECIMAL(12,2) NOT NULL,
  FOREIGN KEY (`order_id`) REFERENCES `dt_order`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`product_id`) REFERENCES `dt_product`(`id`),
  INDEX `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单商品表';

-- ----------------------------
-- 8. 商品评价表
-- ----------------------------
DROP TABLE IF EXISTS `dt_product_review`;
CREATE TABLE `dt_product_review` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `rating` TINYINT NOT NULL COMMENT '评分 1-5',
  `content` TEXT,
  `images` JSON COMMENT '评价图片数组',
  `is_anonymous` TINYINT DEFAULT 0 COMMENT '0-否 1-是',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`order_id`) REFERENCES `dt_order`(`id`),
  FOREIGN KEY (`product_id`) REFERENCES `dt_product`(`id`),
  FOREIGN KEY (`user_id`) REFERENCES `dt_user`(`id`),
  INDEX `idx_product_id` (`product_id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';

-- ----------------------------
-- 9. 优惠券表
-- ----------------------------
DROP TABLE IF EXISTS `dt_coupon`;
CREATE TABLE `dt_coupon` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL,
  `type` TINYINT NOT NULL COMMENT '1-满减 2-折扣 3-运费券',
  `discount_value` DECIMAL(10,2) NOT NULL COMMENT '满减金额/折扣百分比',
  `min_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '使用门槛金额',
  `total_count` INT NOT NULL,
  `issued_count` INT DEFAULT 0,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `status` TINYINT DEFAULT 1 COMMENT '0-停用 1-启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_status_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券表';

-- ----------------------------
-- 10. 用户优惠券表
-- ----------------------------
DROP TABLE IF EXISTS `dt_user_coupon`;
CREATE TABLE `dt_user_coupon` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `coupon_id` BIGINT NOT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0-未使用 1-已使用 2-已过期',
  `used_time` DATETIME,
  `order_id` BIGINT COMMENT '使用的订单ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `dt_user`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`coupon_id`) REFERENCES `dt_coupon`(`id`),
  INDEX `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 数据插入
-- ============================================================

-- ----------------------------
-- 用户数据 (20个用户)
-- ----------------------------
INSERT INTO `dt_user` (`username`, `nickname`, `phone`, `email`, `password_hash`, `gender`, `avatar_url`, `status`) VALUES
('zhangwei', '张伟', '13800138001', 'zhangwei@example.com', '$2a$10$hashedpassword1xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, '/avatars/zhangwei.jpg', 1),
('lina', '李娜', '13800138002', 'lina@example.com', '$2a$10$hashedpassword2xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, '/avatars/lina.jpg', 1),
('wangfang', '王芳', '13800138003', 'wangfang@example.com', '$2a$10$hashedpassword3xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, NULL, 1),
('liujun', '刘军', '13800138004', 'liujun@example.com', '$2a$10$hashedpassword4xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, '/avatars/liujun.jpg', 1),
('chenmin', '陈敏', '13800138005', 'chenmin@example.com', '$2a$10$hashedpassword5xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, '/avatars/chenmin.jpg', 1),
('yangjing', '杨静', '13800138006', 'yangjing@example.com', '$2a$10$hashedpassword6xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, NULL, 1),
('zhaolei', '赵磊', '13800138007', 'zhaolei@example.com', '$2a$10$hashedpassword7xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, '/avatars/zhaolei.jpg', 1),
('huangyan', '黄艳', '13800138008', 'huangyan@example.com', '$2a$10$hashedpassword8xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, '/avatars/huangyan.jpg', 1),
('zhouqiang', '周强', '13800138009', 'zhouqiang@example.com', '$2a$10$hashedpassword9xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, NULL, 1),
('wuxia', '吴霞', '13800138010', 'wuxia@example.com', '$2a$10$hashedpassword10xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, '/avatars/wuxia.jpg', 1),
('xupeng', '徐鹏', '13800138011', 'xupeng@example.com', '$2a$10$hashedpassword11xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, '/avatars/xupeng.jpg', 1),
('sunli', '孙丽', '13800138012', 'sunli@example.com', '$2a$10$hashedpassword12xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, NULL, 1),
('majie', '马杰', '13800138013', 'majie@example.com', '$2a$10$hashedpassword13xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, '/avatars/majie.jpg', 1),
('zhuhua', '朱华', '13800138014', 'zhuhua@example.com', '$2a$10$hashedpassword14xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, '/avatars/zhuhua.jpg', 1),
('huling', '胡玲', '13800138015', 'huling@example.com', '$2a$10$hashedpassword15xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, '/avatars/huling.jpg', 1),
('guotao', '郭涛', '13800138016', 'guotao@example.com', '$2a$10$hashedpassword16xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, NULL, 1),
('hebin', '何彬', '13800138017', 'hebin@example.com', '$2a$10$hashedpassword17xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, '/avatars/hebin.jpg', 1),
('luoxue', '罗雪', '13800138018', 'luoxue@example.com', '$2a$10$hashedpassword18xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, '/avatars/luoxue.jpg', 1),
('gaoxin', '高鑫', '13800138019', 'gaoxin@example.com', '$2a$10$hashedpassword19xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, '/avatars/gaoxin.jpg', 1),
('linxi', '林熙', '13800138020', 'linxi@example.com', '$2a$10$hashedpassword20xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 2, '/avatars/linxi.jpg', 1);

-- ----------------------------
-- 收货地址 (30个地址，部分用户有多个)
-- ----------------------------
INSERT INTO `dt_user_address` (`user_id`, `receiver_name`, `phone`, `province`, `city`, `district`, `detail_address`, `is_default`) VALUES
(1, '张伟', '13800138001', '北京市', '北京市', '朝阳区', '建国路88号SOHO现代城A座1201', 1),
(1, '张伟（公司）', '13800138001', '北京市', '北京市', '海淀区', '中关村大街1号海龙大厦8层808', 0),
(2, '李娜', '13800138002', '上海市', '上海市', '浦东新区', '陆家嘴环路1088号恒生银行大厦22楼', 1),
(2, '李娜（家）', '13800138002', '上海市', '上海市', '徐汇区', '漕溪北路555号汇金广场B座1502', 0),
(3, '王芳', '13800138003', '广东省', '广州市', '天河区', '天河路228号正佳广场万豪酒店公寓1806', 1),
(4, '刘军', '13800138004', '广东省', '深圳市', '南山区', '科技园南区高新南一道008号创维大厦C座1001', 1),
(5, '陈敏', '13800138005', '浙江省', '杭州市', '西湖区', '文三路388号华星时代广场A座903', 1),
(5, '陈敏（余杭）', '13800138005', '浙江省', '杭州市', '余杭区', '未来科技城梦想小镇创客空间2号楼301', 0),
(6, '杨静', '13800138006', '江苏省', '南京市', '鼓楼区', '中山北路228号国瑞大厦1608', 1),
(7, '赵磊', '13800138007', '四川省', '成都市', '武侯区', '人民南路四段19号威斯顿联邦大厦2301', 1),
(7, '赵磊（天府）', '13800138007', '四川省', '成都市', '高新区', '天府大道北段966号天府国际金融中心6号楼8层', 0),
(8, '黄艳', '13800138008', '湖北省', '武汉市', '江汉区', '建设大道568号新世界国贸大厦I座1205', 1),
(9, '周强', '13800138009', '重庆市', '重庆市', '渝北区', '金开大道1008号棕榈泉国际花园15栋2801', 1),
(10, '吴霞', '13800138010', '陕西省', '西安市', '雁塔区', '科技路48号创业广场B座2306', 1),
(11, '徐鹏', '13800138011', '山东省', '青岛市', '市南区', '香港中路66号远雄国际广场B座1101', 1),
(12, '孙丽', '13800138012', '天津市', '天津市', '和平区', '南京路183号富顿中心A座1802', 1),
(13, '马杰', '13800138013', '河南省', '郑州市', '金水区', '花园路39号国贸中心3号楼2单元2001', 1),
(14, '朱华', '13800138014', '湖南省', '长沙市', '岳麓区', '岳麓大道158号盛大环球金融中心1203', 1),
(15, '胡玲', '13800138015', '福建省', '厦门市', '思明区', '湖滨南路334号源昌国际中心B座1601', 1),
(16, '郭涛', '13800138016', '安徽省', '合肥市', '蜀山区', '长江西路195号之心城A座2208', 1),
(17, '何彬', '13800138017', '辽宁省', '大连市', '沙河口区', '中山路588号银洲国际大厦B座1005', 1),
(18, '罗雪', '13800138018', '云南省', '昆明市', '盘龙区', '北京路515号俊发中心2101', 1),
(19, '高鑫', '13800138019', '江西省', '南昌市', '红谷滩区', '红谷中大道998号绿地中心双子塔A1座1506', 1),
(20, '林熙', '13800138020', '河北省', '石家庄市', '长安区', '中山东路303号勒泰中心A座2601', 1),
(3, '王芳（父母家）', '13800138003', '广东省', '广州市', '海珠区', '新港中路354号珠江帝景苑C座902', 0),
(4, '刘军（父母家）', '13800138004', '广东省', '深圳市', '福田区', '深南大道6008号特区报业大厦18层', 0),
(6, '杨静（公司）', '13800138006', '江苏省', '南京市', '玄武区', '珠江路688号卓越大厦1202', 0),
(9, '周强（公司）', '13800138009', '重庆市', '重庆市', '江北区', '观音桥步行街9号富力海悦酒店公寓1908', 0),
(13, '马杰（新家）', '13800138013', '河南省', '郑州市', '郑东新区', '商务内环路2号绿地中心千玺广场38层', 0),
(16, '郭涛（滨湖）', '13800138016', '安徽省', '合肥市', '滨湖新区', '庐州大道718号滨湖银泰中心B座2001', 0);

-- ----------------------------
-- 商品分类 (12个一级分类 + 36个二级分类)
-- ----------------------------
INSERT INTO `dt_category` (`id`, `parent_id`, `name`, `icon_url`, `sort_order`, `status`) VALUES
-- 一级分类
(1, 0, '手机数码', '/icons/digital.png', 1, 1),
(2, 0, '电脑办公', '/icons/computer.png', 2, 1),
(3, 0, '家用电器', '/icons/appliance.png', 3, 1),
(4, 0, '服饰鞋包', '/icons/clothing.png', 4, 1),
(5, 0, '美妆个护', '/icons/beauty.png', 5, 1),
(6, 0, '食品生鲜', '/icons/food.png', 6, 1),
(7, 0, '家居家装', '/icons/home.png', 7, 1),
(8, 0, '图书文娱', '/icons/book.png', 8, 1),
(9, 0, '运动户外', '/icons/sports.png', 9, 1),
(10, 0, '母婴玩具', '/icons/baby.png', 10, 1),
(11, 0, '汽车用品', '/icons/auto.png', 11, 1),
(12, 0, '医药保健', '/icons/health.png', 12, 1),
-- 手机数码二级
(13, 1, '智能手机', NULL, 1, 1),
(14, 1, '平板电脑', NULL, 2, 1),
(15, 1, '手机配件', NULL, 3, 1),
(16, 1, '智能穿戴', NULL, 4, 1),
-- 电脑办公二级
(17, 2, '笔记本电脑', NULL, 1, 1),
(18, 2, '台式电脑', NULL, 2, 1),
(19, 2, '电脑配件', NULL, 3, 1),
(20, 2, '办公设备', NULL, 4, 1),
-- 家用电器二级
(21, 3, '厨房电器', NULL, 1, 1),
(22, 3, '生活电器', NULL, 2, 1),
(23, 3, '大家电', NULL, 3, 1),
(24, 3, '个护电器', NULL, 4, 1),
-- 服饰鞋包二级
(25, 4, '女装', NULL, 1, 1),
(26, 4, '男装', NULL, 2, 1),
(27, 4, '鞋靴', NULL, 3, 1),
(28, 4, '箱包', NULL, 4, 1),
-- 美妆个护二级
(29, 5, '面部护肤', NULL, 1, 1),
(30, 5, '彩妆香氛', NULL, 2, 1),
-- 食品生鲜二级
(31, 6, '休闲零食', NULL, 1, 1),
(32, 6, '茶叶冲饮', NULL, 2, 1),
-- 家居家装二级
(33, 7, '家具', NULL, 1, 1),
(34, 7, '家纺', NULL, 2, 1),
-- 运动户外二级
(35, 9, '运动鞋', NULL, 1, 1),
(36, 9, '健身器材', NULL, 2, 1),
-- 母婴玩具二级
(37, 10, '奶粉辅食', NULL, 1, 1),
(38, 10, '儿童玩具', NULL, 2, 1);

-- ----------------------------
-- 商品数据 (80个商品)
-- ----------------------------
INSERT INTO `dt_product` (`category_id`, `name`, `subtitle`, `main_image`, `images`, `price`, `original_price`, `cost_price`, `stock`, `sales`, `status`, `description`) VALUES
-- 智能手机 (13)
(13, '华为 Mate 60 Pro 5G', '卫星通话 超可靠玄武架构', '/products/huawei-mate60pro.jpg', '["/products/huawei-mate60pro-1.jpg","/products/huawei-mate60pro-2.jpg","/products/huawei-mate60pro-3.jpg"]', 6999.00, 7999.00, 4200.00, 580, 12680, 1, '搭载麒麟9000S芯片，支持卫星通话，6.82英寸等深四曲屏，5000mAh大电池，IP68防尘抗水'),
(13, 'iPhone 15 Pro Max', 'A17 Pro芯片 钛金属设计', '/products/iphone15promax.jpg', '["/products/iphone15promax-1.jpg","/products/iphone15promax-2.jpg"]', 9999.00, 9999.00, 6500.00, 320, 25430, 1, 'A17 Pro芯片，钛金属边框，5倍光学变焦，USB-C接口，Action Button操作按钮'),
(13, '小米14 Ultra', '徕卡光学Summilux镜头', '/products/xiaomi14ultra.jpg', '["/products/xiaomi14ultra-1.jpg","/products/xiaomi14ultra-2.jpg"]', 5999.00, 6499.00, 3600.00, 450, 8920, 1, '骁龙8Gen3，徕卡Summilux光学镜头，1英寸无级可变光圈，5300mAh电池+90W快充'),
(13, 'OPPO Find X7 Ultra', '双潜望长焦 哈苏影像', '/products/oppo-findx7.jpg', '["/products/oppo-findx7-1.jpg","/products/oppo-findx7-2.jpg"]', 5499.00, 5999.00, 3300.00, 380, 5670, 1, '骁龙8Gen3，双潜望长焦镜头，哈苏自然色彩，5000mAh电池+100W超级闪充'),
(13, 'vivo X100 Pro', '蔡司APO长焦 天玑9300', '/products/vivo-x100pro.jpg', '["/products/vivo-x100pro-1.jpg","/products/vivo-x100pro-2.jpg"]', 4999.00, 5499.00, 3000.00, 520, 7340, 1, '天玑9300旗舰芯片，蔡司APO超级长焦，5400mAh蓝海电池+100W闪充'),
-- 平板电脑 (14)
(14, 'iPad Pro 12.9英寸 M2', 'M2芯片 Liquid视网膜XDR', '/products/ipad-pro-129.jpg', '["/products/ipad-pro-129-1.jpg"]', 8999.00, 8999.00, 5800.00, 200, 4560, 1, 'M2芯片，12.9英寸Liquid视网膜XDR显示屏，ProMotion自适应刷新率，Thunderbolt接口'),
(14, '华为 MatePad Pro 13.2', '柔性OLED大屏 星闪连接', '/products/huawei-matepad-pro.jpg', '["/products/huawei-matepad-pro-1.jpg"]', 5699.00, 6199.00, 3400.00, 280, 3210, 1, '13.2英寸柔性OLED屏幕，麒麟9000S，星闪连接技术，10100mAh电池'),
(14, '小米平板6 Max 14', '14英寸大屏 骁龙8+ Gen1', '/products/xiaomi-pad6max.jpg', '["/products/xiaomi-pad6max-1.jpg"]', 3299.00, 3699.00, 2000.00, 350, 2890, 1, '14英寸2.8K 120Hz屏幕，骁龙8+ Gen1，10000mAh大电池，67W快充'),
-- 手机配件 (15)
(15, '安克 65W氮化镓充电器', '小巧便携 多协议兼容', '/products/anker-65w.jpg', '["/products/anker-65w-1.jpg"]', 128.00, 168.00, 45.00, 5000, 32000, 1, '65W大功率，氮化镓技术，体积缩减30%，支持PD3.0/QC4+等多协议快充'),
(15, '绿联 磁吸无线充电宝', 'MagSafe兼容 5000mAh', '/products/ugreen-wireless-bank.jpg', '["/products/ugreen-wireless-bank-1.jpg"]', 149.00, 199.00, 55.00, 3200, 18500, 1, '15W磁吸无线充电，5000mAh容量，超薄设计，支持边充边放'),
(15, '闪迪 256GB TF存储卡', '高速读写 行车记录仪专用', '/products/sandisk-256gb.jpg', '["/products/sandisk-256gb-1.jpg"]', 109.00, 159.00, 48.00, 8000, 45000, 1, '256GB大容量，读取速度100MB/s，A1应用性能等级，宽温设计'),
-- 智能穿戴 (16)
(16, 'Apple Watch Series 9', 'S9芯片 双指轻点手势', '/products/apple-watch-s9.jpg', '["/products/apple-watch-s9-1.jpg"]', 2999.00, 3199.00, 1800.00, 400, 11200, 1, 'S9 SiP芯片，双指轻点手势操作，2000尼特亮度，血氧和心电图功能'),
(16, '华为 Watch GT 4', '八通道心率 蓝牙通话', '/products/huawei-watch-gt4.jpg', '["/products/huawei-watch-gt4-1.jpg"]', 1488.00, 1688.00, 750.00, 600, 9800, 1, '1.43英寸AMOLED高清屏幕，八通道心率检测，蓝牙通话，14天超长续航'),
(16, '小米手环8 Pro', '1.74英寸大屏 独立GPS', '/products/xiaomi-band8pro.jpg', '["/products/xiaomi-band8pro-1.jpg"]', 349.00, 399.00, 140.00, 2000, 28000, 1, '1.74英寸AMOLED大屏，内置独立GPS，150+运动模式，14天续航'),
-- 笔记本电脑 (17)
(17, '联想 ThinkPad X1 Carbon Gen11', '14英寸轻薄商务本 i7-1365U', '/products/thinkpad-x1c.jpg', '["/products/thinkpad-x1c-1.jpg","/products/thinkpad-x1c-2.jpg"]', 10999.00, 12999.00, 7200.00, 150, 5680, 1, '14英寸2.8K OLED屏，i7-1365U处理器，16GB+1TB SSD，碳纤维材质仅1.12kg'),
(17, 'MacBook Pro 14 M3 Pro', 'M3 Pro芯片 18GB统一内存', '/products/macbook-pro-14.jpg', '["/products/macbook-pro-14-1.jpg","/products/macbook-pro-14-2.jpg"]', 14999.00, 14999.00, 9800.00, 180, 8900, 1, 'M3 Pro芯片，18GB统一内存，512GB SSD，Liquid视网膜XDR屏，17小时续航'),
(17, '华为 MateBook X Pro 2024', '14.2英寸OLED i7-1360P', '/products/matebook-xpro.jpg', '["/products/matebook-xpro-1.jpg"]', 9999.00, 10999.00, 6500.00, 200, 4320, 1, '14.2英寸3.1K OLED柔性屏，i7-1360P，16GB+1TB，金属机身仅980g'),
(17, '戴尔 XPS 13 Plus', '13.4英寸+屏 i7-1360T', '/products/dell-xps13plus.jpg', '["/products/dell-xps13plus-1.jpg"]', 8999.00, 10499.00, 5800.00, 120, 3200, 1, '13.4英寸3.5K OLED触控屏，i7-1360T，16GB+512GB，无缝玻璃触控板'),
-- 台式电脑 (18)
(18, '联想 GeekPro 设计师台式机', 'i7-13700F RTX4060Ti 16G', '/products/geekpro.jpg', '["/products/geekpro-1.jpg"]', 6499.00, 7299.00, 4500.00, 80, 2100, 1, 'i7-13700F处理器，RTX4060Ti显卡，16GB DDR5+1TB SSD，240水冷散热'),
(18, '苹果 Mac Mini M2', 'M2芯片 紧凑机身', '/products/mac-mini-m2.jpg', '["/products/mac-mini-m2-1.jpg"]', 4499.00, 4499.00, 3000.00, 250, 6700, 1, 'M2芯片，8GB统一内存，256GB SSD，Thunderbolt 4接口，紧凑设计'),
-- 电脑配件 (19)
(19, '罗技 MX Master 3S', '人体工学 静音点击 8K DPI', '/products/mx-master-3s.jpg', '["/products/mx-master-3s-1.jpg"]', 599.00, 699.00, 260.00, 3000, 42000, 1, '人体工学设计，静音点击技术，8000 DPI传感器，70天超长续航，USB-C快充'),
(19, '三星 990 Pro 2TB NVMe SSD', 'PCIe 4.0 旗舰固态', '/products/samsung-990pro.jpg', '["/products/samsung-990pro-1.jpg"]', 1099.00, 1399.00, 650.00, 1500, 15000, 1, 'PCIe 4.0 NVMe，顺序读取7450MB/s，写入6900MB/s，2TB容量，五年质保'),
(19, '戴尔 U2723QE 27寸4K显示器', 'IPS Black Type-C 90W', '/products/dell-u2723qe.jpg', '["/products/dell-u2723qe-1.jpg"]', 3299.00, 3899.00, 2100.00, 200, 5600, 1, '27英寸4K IPS Black面板，99%sRGB色域，Type-C 90W反向充电，硬件低蓝光'),
-- 办公设备 (20)
(20, '惠普 LaserJet Pro M404dw', '黑白激光 自动双面打印', '/products/hp-m404dw.jpg', '["/products/hp-m404dw-1.jpg"]', 1899.00, 2299.00, 1100.00, 100, 3400, 1, '黑白激光打印，40页/分钟高速，自动双面打印，有线+无线双连接'),
(20, '爱普生 L3251 墨仓式一体机', '打印复印扫描 无线WiFi', '/products/epson-l3251.jpg', '["/products/epson-l3251-1.jpg"]', 899.00, 1099.00, 520.00, 180, 8900, 1, '墨仓式大容量，打印复印扫描一体，无线WiFi连接，打印成本低至1分/页'),
-- 厨房电器 (21)
(21, '美的 空气炸锅 MF-KZE5089', '5L大容量 可视窗口', '/products/midea-airfryer.jpg', '["/products/midea-airfryer-1.jpg"]', 299.00, 399.00, 150.00, 2000, 25000, 1, '5L大容量适合3-5人，360°热风循环，可视窗口设计，智能触控面板'),
(21, '九阳 破壁豆浆机 DJ10R-K16G', '免滤直饮 降噪静音', '/products/joyoung-soymilk.jpg', '["/products/joyoung-soymilk-1.jpg"]', 399.00, 499.00, 200.00, 1500, 18000, 1, '破壁免滤直饮，多重降噪静音设计，1-4人容量，智能预约功能'),
(21, '苏泊尔 电饭煲 SF40HC88', '本釜内胆 IH电磁加热', '/products/supor-ricecooker.jpg', '["/products/supor-ricecooker-1.jpg"]', 599.00, 699.00, 320.00, 1200, 12000, 1, '本釜内胆聚能锁热，IH电磁均匀加热，4L容量，24小时智能预约'),
-- 生活电器 (22)
(22, '戴森 V15 Detect 吸尘器', '激光探测 压电传感', '/products/dyson-v15.jpg', '["/products/dyson-v15-1.jpg"]', 4990.00, 5490.00, 2800.00, 300, 9800, 1, '激光纤薄软绒吸头，压电式声学传感器自动计数，60分钟续航，整机过滤'),
(22, '科沃斯 T20 Pro 扫地机器人', '热水洗拖布 自动集尘', '/products/ecovacs-t20pro.jpg', '["/products/ecovacs-t20pro-1.jpg"]', 3999.00, 4599.00, 2200.00, 400, 7600, 1, '55°C热水洗拖布，自动集尘+自动上下水，7000Pa大吸力，AIVI 3D避障'),
(22, '美的 加湿器 SZ-2W40', '无雾冷蒸发 上加水', '/products/midea-humidifier.jpg', '["/products/midea-humidifier-1.jpg"]', 199.00, 259.00, 90.00, 2500, 15000, 1, '无雾冷蒸发技术，上加水设计方便，4L大容量，智能恒湿+抗菌滤芯'),
-- 大家电 (23)
(23, '海尔 冰箱 BCD-501WGHSS', '501L十字对开门 风冷无霜', '/products/haier-fridge-501.jpg', '["/products/haier-fridge-501-1.jpg"]', 3999.00, 4999.00, 2500.00, 100, 6500, 1, '501L大容量十字对开门，风冷无霜，双变频节能，DEO净味抗菌，一级能效'),
(23, '格力 空调 KFR-35GW/NhBb3Bj', '1.5匹 新一级能效 变频冷暖', '/products/gree-ac-1.5.jpg', '["/products/gree-ac-1.5-1.jpg"]', 2799.00, 3299.00, 1700.00, 200, 18000, 1, '1.5匹新一级能效，全直流变频，56°C高温自清洁，独立除湿，WiFi智控'),
(23, '小天鹅 洗衣机 TG100VT8M20IT', '10kg 洗烘一体 蒸汽除菌', '/products/little-swan-washer.jpg', '["/products/little-swan-washer-1.jpg"]', 2999.00, 3599.00, 1900.00, 150, 11000, 1, '10kg洗涤+7kg烘干，蒸汽除菌除螨，BLDC变频电机，1400转脱水'),
-- 个护电器 (24)
(24, '飞利浦 电动牙刷 HX6856', '声波震动 智能压力感应', '/products/philips-toothbrush.jpg', '["/products/philips-toothbrush-1.jpg"]', 399.00, 499.00, 180.00, 3000, 22000, 1, '31000次/分钟声波震动，智能压力感应保护牙龈，3种模式2档强度，USB充电'),
(24, '戴森 Supersonic HD15 吹风机', '智能温控 负离子护发', '/products/dyson-hd15.jpg', '["/products/dyson-hd15-1.jpg"]', 2990.00, 3290.00, 1700.00, 250, 8500, 1, 'V9数码马达，智能温控技术，负离子护发，5款造型风嘴，快速干发'),
-- 女装 (25)
(25, '优衣库 女装 圆领针织衫', '柔软舒适 多色可选', '/products/uniqlo-knit.jpg', '["/products/uniqlo-knit-1.jpg"]', 149.00, 199.00, 65.00, 5000, 35000, 1, '优质棉混纺面料，柔软亲肤透气，经典圆领设计，多色可选百搭'),
(25, '波司登 女士羽绒服 B20142322', '90%白鹅绒 轻薄保暖', '/products/bosideng-down-w.jpg', '["/products/bosideng-down-w-1.jpg"]', 899.00, 1299.00, 400.00, 800, 12000, 1, '90%高品质白鹅绒填充，轻薄面料防风防泼水，立体剪裁修身版型，-15°C保暖'),
(25, '太平鸟 女士连衣裙 A1GBC2311', '法式碎花 收腰显瘦', '/products/peacenest-dress.jpg', '["/products/peacenest-dress-1.jpg"]', 259.00, 359.00, 110.00, 1200, 8600, 1, '法式碎花印花，高腰收腰设计，A字裙摆显瘦，雪纺面料轻盈飘逸'),
-- 男装 (26)
(26, '海澜之家 男士商务休闲裤', '弹力面料 修身直筒', '/products/hla-pants.jpg', '["/products/hla-pants-1.jpg"]', 169.00, 229.00, 70.00, 4000, 28000, 1, '弹力面料舒适不紧绷，修身直筒版型，商务休闲两相宜，抗皱易打理'),
(26, '雅戈尔 男士长袖衬衫 DP纯棉免烫', '100%棉 商务正装', '/products/youngor-shirt.jpg', '["/products/youngor-shirt-1.jpg"]', 359.00, 459.00, 160.00, 2000, 15000, 1, '100%纯棉面料，DP纯棉免烫工艺，经典尖领设计，商务正装首选'),
(26, '李宁 男士运动卫衣', '圆领套头 透气吸汗', '/products/lining-hoodie.jpg', '["/products/lining-hoodie-1.jpg"]', 199.00, 269.00, 85.00, 3000, 19000, 1, '棉涤混纺面料，透气吸汗速干，圆领套头设计，运动休闲百搭'),
-- 鞋靴 (27)
(27, '耐克 Air Force 1 \'07', '经典小白鞋 百搭板鞋', '/products/nike-af1.jpg', '["/products/nike-af1-1.jpg"]', 749.00, 749.00, 350.00, 2000, 52000, 1, '经典空军一号设计，优质皮革鞋面，Air-Sole气垫缓震，百搭永不过时'),
(27, '阿迪达斯 UltraBoost 23', 'Boost中底 跑步运动鞋', '/products/adidas-ultraboost.jpg', '["/products/adidas-ultraboost-1.jpg"]', 999.00, 1299.00, 480.00, 1500, 18000, 1, 'Boost能量回馈中底，Primeknit+编织鞋面，Continental™马牌橡胶外底'),
(27, '百丽 女士羊皮高跟鞋', '真皮细跟 优雅通勤', '/products/belle-heels.jpg', '["/products/belle-heels-1.jpg"]', 599.00, 799.00, 260.00, 800, 9500, 1, '优质羊皮鞋面，8cm纤细跟高，防水台设计减轻脚压，优雅通勤首选'),
-- 箱包 (28)
(28, '新秀丽 商务双肩包 BP5', '15.6寸电脑隔层 防泼水', '/products/samsonite-bp5.jpg', '["/products/samsonite-bp5-1.jpg"]', 399.00, 499.00, 180.00, 1500, 12000, 1, '15.6寸笔记本电脑隔层，防泼水面料，人体工学背垫，多功能收纳分区'),
(28, 'Coach 女士托特包 City33', '十字纹牛皮 大容量', '/products/coach-tote.jpg', '["/products/coach-tote-1.jpg"]', 2950.00, 3500.00, 1400.00, 300, 4800, 1, '十字纹牛皮材质，大容量托特设计，拉链封口安全，多色可选'),
(28, '外交官 行李箱 TEC-9系列 24寸', 'PC+ABS 万向轮 海关锁', '/products/diplomat-luggage.jpg', '["/products/diplomat-luggage-1.jpg"]', 459.00, 599.00, 220.00, 1000, 21000, 1, 'PC+ABS抗压材质，360°静音万向轮，TSA海关锁，24寸适合5-7天出行'),
-- 面部护肤 (29)
(29, 'SK-II 神仙水 230ml', 'PITERA™精华 嫩肤精华露', '/products/skii-facial.jpg', '["/products/skii-facial-1.jpg"]', 1370.00, 1540.00, 550.00, 800, 28000, 1, '超过90%PITERA™精华成分，改善肌肤五大维度，经典护肤精华露'),
(29, '兰蔻 小黑瓶精华肌底液 100ml', '二裂酵母修护 强韧屏障', '/products/lancome-serum.jpg', '["/products/lancome-serum-1.jpg"]', 1080.00, 1280.00, 420.00, 600, 22000, 1, '二裂酵母发酵产物溶胞物，修护肌底强韧屏障，蛋清质地易吸收'),
(29, '珀莱雅 双抗精华 3.0版 30ml', '抗糖抗氧化 提亮肤色', '/products/proya-serum.jpg', '["/products/proya-serum-1.jpg"]', 238.00, 298.00, 80.00, 5000, 45000, 1, '麦角硫因+虾青素双效抗氧，脱羧肌肽抗糖化，3.0升级配方提亮肤色'),
-- 彩妆香氛 (30)
(30, 'YSL 圣罗兰 小金条口红 #21', '哑光丝绒 经典复古红', '/products/ysl-lipstick-21.jpg', '["/products/ysl-lipstick-21-1.jpg"]', 335.00, 335.00, 120.00, 2000, 38000, 1, '哑光丝绒质地，#21经典复古红色，薄涂厚涂皆宜，持久不脱色'),
(30, '迪奥 花漾甜心淡香水 50ml', '清新花香 优雅温柔', '/products/dior-perfume.jpg', '["/products/dior-perfume-1.jpg"]', 699.00, 799.00, 280.00, 500, 15000, 1, '清新花香调，前调柑橘+中调牡丹+尾调白麝香，优雅温柔气质'),
(30, '花西子 散粉 玉容睡莲版', '控油定妆 轻薄细腻', '/products/florasis-powder.jpg', '["/products/florasis-powder-1.jpg"]', 149.00, 179.00, 45.00, 4000, 32000, 1, '睡莲精粹成分，轻薄粉质不堵毛孔，控油定妆持久，适合亚洲肤色'),
-- 休闲零食 (31)
(31, '三只松鼠 每日坚果 750g/30包', '6种坚果果干 科学配比', '/products/3squirrels-nuts.jpg', '["/products/3squirrels-nuts-1.jpg"]', 89.00, 129.00, 38.00, 10000, 85000, 1, '核桃/腰果/扁桃仁/榛子/蔓越莓/黑加仑6种搭配，每日1包科学定量'),
(31, '良品铺子 猪肉脯 200g', '靖江特产 蜜汁口味', '/products/bestore-porkjerky.jpg', '["/products/bestore-porkjerky-1.jpg"]', 29.90, 39.90, 12.00, 8000, 62000, 1, '靖江特产猪肉脯，精选后腿肉，蜜汁调味鲜甜，独立小包装方便'),
(31, '卫龙 辣条大面筋 650g 怀旧装', '经典辣味 儿时的味道', '/products/weilao-gluten.jpg', '["/products/weilao-gluten-1.jpg"]', 19.90, 25.90, 8.00, 15000, 120000, 1, '经典辣条面筋味，甜辣适中回味无穷，儿时校园零食记忆，大分量分享装'),
-- 茶叶冲饮 (32)
(32, '大益 普洱茶 7572熟茶饼 357g', '勐海茶厂 经典标杆', '/products/dayi-puer.jpg', '["/products/dayi-puer-1.jpg"]', 198.00, 268.00, 90.00, 2000, 8500, 1, '勐海茶厂经典7572配方，熟茶发酵工艺，汤色红浓明亮，陈香醇厚'),
(32, '西湖牌 明前特级龙井 250g', '核心产区 手工炒制', '/products/westlake-longjing.jpg', '["/products/westlake-longjing-1.jpg"]', 288.00, 388.00, 120.00, 1500, 12000, 1, '西湖核心产区明前茶，一芽一叶标准，手工炒制，豆花香明显回甘持久'),
(32, '隅田川 挂耳咖啡 意式风味 24片', '日本进口技术 现磨口感', '/products/tasogare-coffee.jpg', '["/products/tasogare-coffee-1.jpg"]', 49.90, 69.90, 20.00, 6000, 55000, 1, '日本进口滤挂技术，深度烘焙意式风味，热水30秒即冲，便携现磨口感'),
-- 家具 (33)
(33, '林氏木业 北欧实木餐桌 1.4m', '白蜡木 原木色简约', '/products/linus-diningtable.jpg', '["/products/linus-diningtable-1.jpg"]', 1899.00, 2499.00, 900.00, 150, 3200, 1, '北美白蜡木实木材质，榫卯结构稳固，1.4m适合4-6人，环保水性漆'),
(33, '源氏木语 实木书架 橡木材质', '五层置物架 北欧简约', '/products/gens-bookshelf.jpg', '["/products/gens-bookshelf-1.jpg"]', 1299.00, 1699.00, 600.00, 200, 4500, 1, '100%纯橡木材质，五层大容量置物，北欧简约风格，承重强稳固'),
(33, '全友 布艺沙发 三人位', '科技布面料 高密度海绵', '/products/quanyou-sofa.jpg', '["/products/quanyou-sofa-1.jpg"]', 2599.00, 3299.00, 1200.00, 100, 5800, 1, '科技布面料防水易清洁，高密度海绵坐感舒适，实木内架坚固耐用'),
-- 家纺 (34)
(34, '罗莱 四件套 纯棉贡缎 1.8m床', '60支长绒棉 亲肤柔软', '/products/luolai-bedding.jpg', '["/products/luolai-bedding-1.jpg"]', 599.00, 799.00, 260.00, 1500, 18000, 1, '60支新疆长绒棉贡缎面料，丝滑光泽亲肤，含床单+被套+枕套*2'),
(34, '水星家纺 抗菌大豆纤维被 200x230cm', '四季通用 柔软蓬松', '/products/mercury-quilt.jpg', '["/products/mercury-quilt-1.jpg"]', 299.00, 399.00, 130.00, 2000, 25000, 1, '大豆蛋白纤维填充，AAA级抗菌面料，四季通用厚度，柔软蓬松保暖'),
(34, '洁丽雅 毛巾浴巾三件套', '纯棉A类 吸水速干', '/products/grace-towel.jpg', '["/products/grace-towel-1.jpg"]', 69.90, 89.90, 25.00, 5000, 42000, 1, 'A类婴幼儿标准纯棉，3秒快速吸水，抗菌防螨，毛巾+浴巾+方巾三件套'),
-- 运动鞋 (35)
(35, '安踏 跑步鞋 马赫4代', '氮科技中底 回弹78%', '/products/anta-running.jpg', '["/products/anta-running-1.jpg"]', 399.00, 499.00, 180.00, 2500, 22000, 1, '氮科技中底回弹率78%，工程网布鞋面透气，耐磨橡胶大底，适合5-15km跑步'),
(35, '新百伦 复古运动鞋 574系列', '经典翻毛皮 百搭休闲', '/products/nb-574.jpg', '["/products/nb-574-1.jpg"]', 699.00, 799.00, 320.00, 1800, 16000, 1, '经典574复古鞋型，翻毛皮+网面拼接，ENCAP中底支撑，日常通勤百搭'),
-- 健身器材 (36)
(36, 'Keep 智能动感单车 Mini版', '磁控阻力 直播跟练', '/products/keep-bike.jpg', '["/products/keep-bike-1.jpg"]', 1299.00, 1599.00, 650.00, 400, 8900, 1, '32档磁控阻力调节，APP直播课程跟练，静音皮带传动，占地仅0.5㎡'),
(36, '迪卡侬 可调节哑铃 20kg/只', '快速调重 家用健身', '/products/decathlon-dumbbell.jpg', '["/products/decathlon-dumbbell-1.jpg"]', 499.00, 599.00, 240.00, 800, 6500, 1, '2.5-20kg快速调节，旋转刻度盘调重，一对装含收纳底座，家用健身首选'),
(36, '舒华 跑步机 SH-T5519', '商用级家用 折叠静音', '/products/shua-treadmill.jpg', '["/products/shua-treadmill-1.jpg"]', 3499.00, 4299.00, 1800.00, 150, 4200, 1, '商用级马达稳定耐用，液压折叠省空间，硅胶减震护膝，140cm宽跑带'),
-- 奶粉辅食 (37)
(37, '爱他美 白金版 婴幼儿配方奶粉 3段 800g', '适合1-3岁 德国进口', '/products/aptamil-formula.jpg', '["/products/aptamil-formula-1.jpg"]', 268.00, 318.00, 130.00, 3000, 25000, 1, '德国原装进口，白金版配方升级，含DHA/ARA+益生元组合，适合1-3岁婴幼儿'),
(37, '嘉宝 有机米粉 燕麦口味 250g', '6个月+ 辅食首选', '/products/gerber-cereal.jpg', '["/products/gerber-cereal-1.jpg"]', 59.00, 79.00, 25.00, 5000, 35000, 1, '有机认证燕麦原料，富含铁锌钙，6个月+宝宝辅食，冲调方便细腻'),
-- 儿童玩具 (38)
(38, '乐高 城市系列 60367 客运火车', '1486颗粒 7个人仔', '/products/lego-train.jpg', '["/products/lego-train-1.jpg"]', 899.00, 999.00, 450.00, 500, 5200, 1, '1486颗粒大套装，含客运火车+轨道+7个人仔，电动马达可驱动，适合8岁+'),
(38, '泡泡玛特 MOLLY的一天 盲盒手办', '随机款式 收藏摆件', '/products/popmart-molly.jpg', '["/products/popmart-molly-1.jpg"]', 69.00, 69.00, 22.00, 8000, 95000, 1, 'MOLLY经典系列盲盒，12款基础+2款隐藏，PVC材质精细涂装，桌面收藏摆件'),
-- 汽车用品 (11 - 直接二级)
(11, '3M 汽车玻璃水 0°C 2L*2瓶', '去油膜虫胶 不留水痕', '/products/3m-washer.jpg', '["/products/3m-washer-1.jpg"]', 29.90, 39.90, 10.00, 6000, 48000, 1, '3M配方去油膜虫胶，0°C防冻配方，不留水痕水渍，2L大容量2瓶装'),
(11, '固特异 汽车脚垫 全包围丝圈', '专车定制 防水防滑', '/products/goodyear-mat.jpg', '["/products/goodyear-mat-1.jpg"]', 198.00, 298.00, 75.00, 2000, 15000, 1, '高密度丝圈材质，全包围专车定制，防水防滑易清洗，环保无异味'),
-- 医药保健 (12 - 直接二级)
(12, '汤臣倍健 蛋白粉 450g', '动植物双蛋白 增强免疫', '/products/by-health-protein.jpg', '["/products/by-health-protein-1.jpg"]', 198.00, 258.00, 80.00, 3000, 28000, 1, '动植物双蛋白配比，PDCAAS满分蛋白质，增强免疫力易吸收，450g罐装'),
(12, '同仁堂 阿胶糕 即食型 300g', '东阿阿胶 补血养颜', '/products/trt-ejiao.jpg', '["/products/trt-ejiao-1.jpg"]', 168.00, 228.00, 70.00, 2500, 16000, 1, '东阿阿胶原料，即食开袋即吃，补血养颜调理，核桃芝麻辅料营养丰富'),
(12, 'Swisse 深海鱼油软胶囊 400粒', '澳洲进口 高浓度Omega-3', '/products/swisse-fishoil.jpg', '["/products/swisse-fishoil-1.jpg"]', 149.00, 199.00, 65.00, 4000, 38000, 1, '澳洲原装进口，高浓度Omega-3 EPA+DHA，保护心脑血管健康，400粒大容量');

-- ----------------------------
-- 购物车数据 (部分用户有购物车)
-- ----------------------------
INSERT INTO `dt_cart_item` (`user_id`, `product_id`, `quantity`, `selected`) VALUES
(1, 1, 1, 1),   -- 张伟: 华为Mate60 Pro
(1, 14, 1, 1),  -- 安克充电器
(1, 29, 1, 0),  -- 罗技鼠标
(2, 5, 1, 1),   -- 李娜: vivo X100 Pro
(2, 33, 1, 1),  -- SK-II神仙水
(2, 47, 1, 1),  -- YSL口红
(3, 8, 1, 1),   -- 王芳: iPad Pro
(3, 31, 2, 1),  -- 三只松鼠每日坚果 x2
(4, 3, 1, 1),   -- 刘军: 小米14 Ultra
(4, 52, 1, 1),  -- 海澜之家休闲裤
(5, 11, 1, 1),  -- 陈敏: MacBook Pro
(5, 44, 1, 1),  -- 兰蔻小黑瓶
(5, 64, 1, 1),  -- 罗莱四件套
(6, 17, 1, 1),  -- 杨静: 华为MatePad Pro
(6, 36, 1, 0),  -- 小米手环8 Pro
(7, 2, 1, 1),   -- 赵磊: iPhone 15 Pro Max
(7, 56, 1, 1),  -- 耐克AF1
(7, 51, 1, 1),  -- 戴森吸尘器
(8, 34, 1, 1),  -- 黄艳: 兰蔻小黑瓶
(8, 48, 1, 1),  -- 迪奥香水
(9, 6, 1, 1),   -- 周强: OPPO Find X7
(9, 68, 1, 1),  -- 新秀丽双肩包
(10, 23, 1, 1), -- 吴霞: 美的空气炸锅
(10, 40, 1, 1); -- 花西子散粉

-- ----------------------------
-- 优惠券数据
-- ----------------------------
INSERT INTO `dt_coupon` (`id`, `name`, `type`, `discount_value`, `min_amount`, `total_count`, `issued_count`, `start_time`, `end_time`, `status`) VALUES
(1, '新人专享满100减20', 1, 20.00, 100.00, 50000, 42300, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1),
(2, '数码家电满2000减200', 1, 200.00, 2000.00, 10000, 6800, '2026-03-01 00:00:00', '2026-06-30 23:59:59', 1),
(3, '服饰鞋包满300减50', 1, 50.00, 300.00, 20000, 15600, '2026-04-01 00:00:00', '2026-05-31 23:59:59', 1),
(4, '美妆满500减80', 1, 80.00, 500.00, 15000, 11200, '2026-04-01 00:00:00', '2026-06-30 23:59:59', 1),
(5, '食品满99减15', 1, 15.00, 99.00, 30000, 22500, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1),
(6, '全品类8折券', 2, 20.00, 200.00, 5000, 3200, '2026-04-15 00:00:00', '2026-05-15 23:59:59', 1),
(7, '618大促满500减100', 1, 100.00, 500.00, 100000, 0, '2026-06-01 00:00:00', '2026-06-18 23:59:59', 1),
(8, '免运费券', 3, 15.00, 0.00, 50000, 38000, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1);

-- ----------------------------
-- 用户优惠券 (用户领券)
-- ----------------------------
INSERT INTO `dt_user_coupon` (`user_id`, `coupon_id`, `status`, `used_time`, `order_id`) VALUES
-- 张伟领券和使用
(1, 1, 1, '2026-02-15 10:30:00', 1),   -- 新人券已使用
(1, 2, 1, '2026-03-20 14:20:00', 2),   -- 数码券已使用
(1, 8, 0, NULL, NULL),                   -- 免运费券未使用
-- 李娜
(2, 1, 1, '2026-02-20 09:15:00', 3),
(2, 4, 1, '2026-04-05 16:40:00', 4),
(2, 8, 0, NULL, NULL),
-- 王芳
(3, 1, 1, '2026-03-01 11:20:00', 5),
(3, 6, 0, NULL, NULL),
-- 刘军
(4, 1, 1, '2026-03-10 08:50:00', 6),
(4, 3, 1, '2026-04-12 20:30:00', 7),
-- 陈敏
(5, 1, 1, '2026-02-28 15:10:00', 8),
(5, 4, 0, NULL, NULL),
(5, 8, 0, NULL, NULL),
-- 杨静
(6, 1, 1, '2026-03-15 12:00:00', 9),
(6, 8, 0, NULL, NULL),
-- 赵磊
(7, 1, 1, '2026-03-05 18:30:00', 10),
(7, 2, 1, '2026-04-01 10:00:00', 11),
(7, 8, 2, NULL, NULL),                   -- 免运费券已过期
-- 黄艳
(8, 1, 1, '2026-03-22 14:45:00', 12),
(8, 4, 1, '2026-04-10 09:20:00', 13),
-- 周强
(9, 1, 1, '2026-03-08 20:10:00', 14),
(9, 5, 1, '2026-04-15 11:30:00', 15),
-- 吴霞
(10, 1, 1, '2026-03-18 08:40:00', 16),
(10, 4, 1, '2026-04-08 15:50:00', 17),
(10, 8, 0, NULL, NULL),
-- 其他用户领券
(11, 1, 1, '2026-03-25 10:20:00', 18),
(11, 5, 0, NULL, NULL),
(12, 1, 1, '2026-04-01 09:00:00', 19),
(12, 3, 0, NULL, NULL),
(13, 1, 1, '2026-04-02 14:30:00', 20),
(13, 2, 0, NULL, NULL),
(14, 1, 0, NULL, NULL),
(14, 8, 0, NULL, NULL),
(15, 1, 0, NULL, NULL),
(16, 1, 0, NULL, NULL),
(17, 5, 0, NULL, NULL),
(18, 4, 0, NULL, NULL),
(19, 1, 0, NULL, NULL),
(20, 1, 0, NULL, NULL);

-- ----------------------------
-- 订单数据 (50个订单，分布在不同用户和不同状态)
-- ----------------------------
INSERT INTO `dt_order` (`order_no`, `user_id`, `address_id`, `total_amount`, `discount_amount`, `shipping_fee`, `pay_amount`, `status`, `pay_type`, `pay_time`, `ship_time`, `receive_time`, `cancel_time`, `remark`) VALUES
-- 张伟的订单 (user_id=1) - 已完成 + 待发货
('ORD20260215103000001', 1, 1, 6999.00, 20.00, 0.00, 6979.00, 3, 1, '2026-02-15 10:35:00', '2026-02-15 14:00:00', '2026-02-17 09:20:00', NULL, NULL),  -- 已完成
('ORD20260320142000002', 1, 1, 2599.00, 200.00, 0.00, 2399.00, 3, 2, '2026-03-20 14:25:00', '2026-03-20 16:00:00', '2026-03-22 18:30:00', NULL, NULL),  -- 已完成
('ORD20260425091000021', 1, 2, 1299.00, 0.00, 0.00, 1299.00, 1, 1, '2026-04-25 09:15:00', '2026-04-25 14:30:00', NULL, NULL, '请发顺丰'),  -- 待发货

-- 李娜的订单 (user_id=2) - 已完成 + 待收货
('ORD20260220091500003', 2, 3, 5148.00, 20.00, 0.00, 5128.00, 3, 1, '2026-02-20 09:20:00', '2026-02-20 11:00:00', '2026-02-22 14:10:00', NULL, NULL),  -- 已完成
('ORD20260405164000004', 2, 3, 1504.00, 80.00, 0.00, 1424.00, 3, 2, '2026-04-05 16:45:00', '2026-04-05 18:00:00', '2026-04-07 10:20:00', NULL, NULL),  -- 已完成
('ORD20260422103000025', 2, 4, 3299.00, 0.00, 0.00, 3299.00, 2, 1, '2026-04-22 10:35:00', '2026-04-22 15:00:00', NULL, NULL, NULL),  -- 待收货

-- 王芳的订单 (user_id=3) - 已完成 + 待付款
('ORD20260301112000005', 3, 5, 9148.00, 20.00, 0.00, 9128.00, 3, 2, '2026-03-01 11:25:00', '2026-03-01 14:00:00', '2026-03-03 09:40:00', NULL, NULL),  -- 已完成
('ORD20260426140000026', 3, 5, 10999.00, 0.00, 0.00, 10999.00, 0, NULL, NULL, NULL, NULL, NULL, NULL),  -- 待付款

-- 刘军的订单 (user_id=4) - 已完成 x2
('ORD20260310085000006', 4, 6, 6148.00, 20.00, 0.00, 6128.00, 3, 1, '2026-03-10 08:55:00', '2026-03-10 11:00:00', '2026-03-12 16:20:00', NULL, NULL),  -- 已完成
('ORD20260412203000007', 4, 6, 169.00, 50.00, 0.00, 119.00, 3, 2, '2026-04-12 20:35:00', '2026-04-13 09:00:00', '2026-04-15 14:30:00', NULL, NULL),  -- 已完成

-- 陈敏的订单 (user_id=5) - 已完成 + 已退款
('ORD20260228151000008', 5, 7, 15079.00, 20.00, 0.00, 15059.00, 3, 1, '2026-02-28 15:15:00', '2026-02-28 17:00:00', '2026-03-02 10:30:00', NULL, NULL),  -- 已完成
('ORD20260408130000022', 5, 7, 1080.00, 0.00, 0.00, 1080.00, 6, 1, '2026-04-08 13:05:00', '2026-04-08 16:00:00', '2026-04-10 09:00:00', NULL, NULL),  -- 已退款

-- 杨静的订单 (user_id=6) - 已完成
('ORD20260315120000009', 6, 9, 5848.00, 20.00, 0.00, 5828.00, 3, 1, '2026-03-15 12:05:00', '2026-03-15 14:30:00', '2026-03-17 11:20:00', NULL, NULL),  -- 已完成

-- 赵磊的订单 (user_id=7) - 已完成 x2 + 已取消
('ORD20260305183000010', 7, 10, 10019.00, 20.00, 0.00, 9999.00, 3, 2, '2026-03-05 18:35:00', '2026-03-05 20:00:00', '2026-03-07 14:50:00', NULL, NULL),  -- 已完成
('ORD20260401100000011', 7, 10, 5739.00, 200.00, 0.00, 5539.00, 3, 1, '2026-04-01 10:05:00', '2026-04-01 13:00:00', '2026-04-03 09:10:00', NULL, NULL),  -- 已完成
('ORD20260418100000027', 7, 11, 599.00, 0.00, 15.00, 614.00, 4, NULL, NULL, NULL, NULL, '2026-04-18 10:30:00', NULL),  -- 已取消

-- 黄艳的订单 (user_id=8) - 已完成 x2
('ORD20260322144500012', 8, 12, 1488.00, 20.00, 0.00, 1468.00, 3, 1, '2026-03-22 14:50:00', '2026-03-22 16:00:00', '2026-03-24 10:20:00', NULL, NULL),  -- 已完成
('ORD20260410092000013', 8, 12, 779.00, 80.00, 0.00, 699.00, 3, 2, '2026-04-10 09:25:00', '2026-04-10 11:00:00', '2026-04-12 15:30:00', NULL, NULL),  -- 已完成

-- 周强的订单 (user_id=9) - 已完成 + 待收货
('ORD20260308201000014', 9, 13, 1299.00, 20.00, 0.00, 1279.00, 3, 2, '2026-03-08 20:15:00', '2026-03-09 09:00:00', '2026-03-11 14:20:00', NULL, NULL),  -- 已完成
('ORD20260415113000015', 9, 13, 189.00, 15.00, 0.00, 174.00, 2, 1, '2026-04-15 11:35:00', '2026-04-15 15:00:00', NULL, NULL, NULL),  -- 待收货

-- 吴霞的订单 (user_id=10) - 已完成 x2
('ORD20260318084000016', 10, 14, 5688.00, 20.00, 0.00, 5668.00, 3, 1, '2026-03-18 08:45:00', '2026-03-18 10:00:00', '2026-03-20 16:30:00', NULL, NULL),  -- 已完成
('ORD20260408155000017', 10, 14, 298.00, 80.00, 0.00, 218.00, 3, 2, '2026-04-08 15:55:00', '2026-04-08 17:00:00', '2026-04-10 11:20:00', NULL, NULL),  -- 已完成

-- 徐鹏的订单 (user_id=11) - 已完成
('ORD20260325102000018', 11, 15, 3499.00, 20.00, 0.00, 3479.00, 3, 1, '2026-03-25 10:25:00', '2026-03-25 14:00:00', '2026-03-27 09:50:00', NULL, NULL),  -- 已完成

-- 孙丽的订单 (user_id=12) - 已完成 + 退款中
('ORD20260401090000019', 12, 16, 2950.00, 20.00, 0.00, 2930.00, 3, 2, '2026-04-01 09:05:00', '2026-04-01 11:00:00', '2026-04-03 14:20:00', NULL, NULL),  -- 已完成
('ORD20260420140000028', 12, 16, 399.00, 0.00, 0.00, 399.00, 5, 1, '2026-04-20 14:05:00', '2026-04-20 16:00:00', NULL, NULL, '商品有质量问题申请退货'),  -- 退款中

-- 马杰的订单 (user_id=13) - 已完成
('ORD20260402143000020', 13, 17, 1899.00, 20.00, 0.00, 1879.00, 3, 1, '2026-04-02 14:35:00', '2026-04-02 16:00:00', '2026-04-04 10:20:00', NULL, NULL),  -- 已完成

-- 朱华的订单 (user_id=14)
('ORD20260410100000029', 14, 18, 2599.00, 0.00, 0.00, 2599.00, 3, 2, '2026-04-10 10:05:00', '2026-04-10 14:00:00', '2026-04-12 09:30:00', NULL, NULL),  -- 已完成

-- 胡玲的订单 (user_id=15)
('ORD20260412110000030', 15, 19, 1370.00, 0.00, 0.00, 1370.00, 3, 1, '2026-04-12 11:05:00', '2026-04-12 14:00:00', '2026-04-14 16:20:00', NULL, NULL),  -- 已完成

-- 郭涛的订单 (user_id=16)
('ORD20260414150000031', 16, 20, 4990.00, 0.00, 0.00, 4990.00, 2, 1, '2026-04-14 15:05:00', '2026-04-14 17:00:00', NULL, NULL, NULL),  -- 待收货

-- 何彬的订单 (user_id=17)
('ORD20260416100000032', 17, 21, 149.00, 0.00, 0.00, 149.00, 3, 2, '2026-04-16 10:05:00', '2026-04-16 14:00:00', '2026-04-18 09:30:00', NULL, NULL),  -- 已完成

-- 罗雪的订单 (user_id=18)
('ORD20260418090000033', 18, 22, 899.00, 0.00, 0.00, 899.00, 3, 1, '2026-04-18 09:05:00', '2026-04-18 11:00:00', '2026-04-20 14:20:00', NULL, NULL),  -- 已完成

-- 高鑫的订单 (user_id=19)
('ORD20260420140000034', 19, 23, 699.00, 0.00, 0.00, 699.00, 2, 1, '2026-04-20 14:05:00', '2026-04-20 16:00:00', NULL, NULL, NULL),  -- 待收货

-- 林熙的订单 (user_id=20)
('ORD20260422100000035', 20, 24, 268.00, 0.00, 0.00, 268.00, 3, 2, '2026-04-22 10:05:00', '2026-04-22 14:00:00', '2026-04-24 09:30:00', NULL, NULL),  -- 已完成

-- 额外补充订单，让部分用户有多笔订单
('ORD20260405100000036', 1, 1, 89.00, 0.00, 0.00, 89.00, 3, 2, '2026-04-05 10:05:00', '2026-04-05 14:00:00', '2026-04-07 11:20:00', NULL, NULL),  -- 张伟 三只松鼠
('ORD20260410110000037', 2, 3, 899.00, 0.00, 0.00, 899.00, 3, 1, '2026-04-10 11:05:00', '2026-04-10 14:00:00', '2026-04-12 16:30:00', NULL, NULL),  -- 李娜 波司登
('ORD20260415150000038', 3, 25, 299.00, 0.00, 0.00, 299.00, 3, 1, '2026-04-15 15:05:00', '2026-04-15 17:00:00', '2026-04-17 10:20:00', NULL, NULL),  -- 王芳 美的空气炸锅
('ORD20260418120000039', 4, 6, 399.00, 0.00, 0.00, 399.00, 2, 2, '2026-04-18 12:05:00', '2026-04-18 15:00:00', NULL, NULL, NULL),  -- 刘军 安踏跑鞋 待收货
('ORD20260420100000040', 5, 8, 199.00, 0.00, 0.00, 199.00, 3, 1, '2026-04-20 10:05:00', '2026-04-20 14:00:00', '2026-04-22 09:30:00', NULL, NULL),  -- 陈敏 花西子
('ORD20260422150000041', 6, 9, 199.00, 0.00, 0.00, 199.00, 0, NULL, NULL, NULL, NULL, NULL, NULL),  -- 杨静 李宁卫衣 待付款
('ORD20260423100000042', 7, 10, 168.00, 0.00, 0.00, 168.00, 3, 1, '2026-04-23 10:05:00', '2026-04-23 14:00:00', '2026-04-25 11:20:00', NULL, NULL),  -- 赵磊 阿胶糕
('ORD20260424110000043', 9, 13, 49.90, 0.00, 0.00, 49.90, 3, 2, '2026-04-24 11:05:00', '2026-04-24 14:00:00', '2026-04-26 10:30:00', NULL, NULL),  -- 周强 挂耳咖啡
('ORD20260425140000044', 10, 14, 349.00, 0.00, 0.00, 349.00, 3, 1, '2026-04-25 14:05:00', '2026-04-25 16:00:00', '2026-04-27 09:20:00', NULL, NULL),  -- 吴霞 小米手环
('ORD20260426100000045', 11, 15, 499.00, 0.00, 0.00, 499.00, 1, 1, '2026-04-26 10:05:00', NULL, NULL, NULL, NULL),  -- 徐鹏 迪卡侬哑铃 待发货
('ORD20260427150000046', 13, 17, 288.00, 0.00, 0.00, 288.00, 3, 2, '2026-04-27 15:05:00', '2026-04-27 17:00:00', '2026-04-29 10:30:00', NULL, NULL),  -- 马杰 龙井茶
('ORD20260428100000047', 15, 19, 699.00, 0.00, 15.00, 714.00, 3, 1, '2026-04-28 10:05:00', '2026-04-28 14:00:00', '2026-04-30 09:30:00', NULL, NULL),  -- 胡玲 百丽高跟鞋
('ORD20260428140000048', 17, 21, 198.00, 0.00, 0.00, 198.00, 1, 2, '2026-04-28 14:05:00', NULL, NULL, NULL, NULL),  -- 何彬 蛋白粉 待发货
('ORD20260429090000049', 19, 23, 899.00, 0.00, 0.00, 899.00, 0, NULL, NULL, NULL, NULL, NULL, NULL),  -- 高鑫 乐高 待付款
('ORD20260429100000050', 20, 24, 109.00, 0.00, 0.00, 109.00, 3, 1, '2026-04-29 10:05:00', '2026-04-29 14:00:00', '2026-05-01 11:20:00', NULL, NULL); -- 林熙 闪迪存储卡

-- ----------------------------
-- 订单商品明细 (对应每个订单购买的商品)
-- ----------------------------
INSERT INTO `dt_order_item` (`order_id`, `product_id`, `product_name`, `product_image`, `unit_price`, `quantity`, `total_price`) VALUES
-- 订单1: 华为Mate60 Pro
(1, 1, '华为 Mate 60 Pro 5G', '/products/huawei-mate60pro.jpg', 6999.00, 1, 6999.00),
-- 订单2: 戴森V15 + 安克充电器
(2, 22, '戴森 V15 Detect 吸尘器', '/products/dyson-v15.jpg', 4990.00, 1, 4990.00),
(2, 14, '安克 65W氮化镓充电器', '/products/anker-65w.jpg', 128.00, 2, 256.00),
-- 订单3: vivo X100 Pro + YSL口红 + SK-II神仙水
(3, 5, 'vivo X100 Pro', '/products/vivo-x100pro.jpg', 4999.00, 1, 4999.00),
(3, 47, 'YSL 圣罗兰 小金条口红 #21', '/products/ysl-lipstick-21.jpg', 335.00, 1, 335.00),
(3, 33, 'SK-II 神仙水 230ml', '/products/skii-facial.jpg', 1370.00, 1, 1370.00),
-- 订单4: 兰蔻小黑瓶 + 迪奥香水
(4, 34, '兰蔻 小黑瓶精华肌底液 100ml', '/products/lancome-serum.jpg', 1080.00, 1, 1080.00),
(4, 48, '迪奥 花漾甜心淡香水 50ml', '/products/dior-perfume.jpg', 699.00, 1, 699.00),
-- 订单5: MacBook Pro + iPad Pro
(5, 11, 'MacBook Pro 14 M3 Pro', '/products/macbook-pro-14.jpg', 14999.00, 1, 14999.00),
-- 订单6: 小米14 Ultra
(6, 3, '小米14 Ultra', '/products/xiaomi14ultra.jpg', 5999.00, 1, 5999.00),
-- 订单7: 海澜之家休闲裤
(7, 52, '海澜之家 男士商务休闲裤', '/products/hla-pants.jpg', 169.00, 1, 169.00),
-- 订单8: 华为MatePad + 三星SSD + 戴尔显示器
(8, 17, '华为 MatePad Pro 13.2', '/products/huawei-matepad-pro.jpg', 5699.00, 1, 5699.00),
(8, 30, '三星 990 Pro 2TB NVMe SSD', '/products/samsung-990pro.jpg', 1099.00, 1, 1099.00),
(8, 31, '戴尔 U2723QE 27寸4K显示器', '/products/dell-u2723qe.jpg', 3299.00, 1, 3299.00),
-- 订单9: 海尔冰箱 + 美的加湿器
(9, 41, '海尔 冰箱 BCD-501WGHSS', '/products/haier-fridge-501.jpg', 3999.00, 1, 3999.00),
(9, 24, '美的 加湿器 SZ-2W40', '/products/midea-humidifier.jpg', 199.00, 1, 199.00),
-- 订单10: iPhone 15 Pro Max
(10, 2, 'iPhone 15 Pro Max', '/products/iphone15promax.jpg', 9999.00, 1, 9999.00),
-- 订单11: 科沃斯扫地机 + 绿联充电宝
(11, 23, '科沃斯 T20 Pro 扫地机器人', '/products/ecovacs-t20pro.jpg', 3999.00, 1, 3999.00),
(11, 15, '绿联 磁吸无线充电宝', '/products/ugreen-wireless-bank.jpg', 149.00, 1, 149.00),
-- 订单12: 华为Watch GT4
(12, 17, '华为 Watch GT 4', '/products/huawei-watch-gt4.jpg', 1488.00, 1, 1488.00),
-- 订单13: 珀莱雅双抗精华 + 花西子散粉
(13, 35, '珀莱雅 双抗精华 3.0版 30ml', '/products/proya-serum.jpg', 238.00, 1, 238.00),
(13, 50, '花西子 散粉 玉容睡莲版', '/products/florasis-powder.jpg', 149.00, 1, 149.00),
-- 订单14: 美的空气炸锅
(14, 21, '美的 空气炸锅 MF-KZE5089', '/products/midea-airfryer.jpg', 299.00, 1, 299.00),
-- 订单15: 三只松鼠每日坚果 x3
(15, 31, '三只松鼠 每日坚果 750g/30包', '/products/3squirrels-nuts.jpg', 89.00, 3, 267.00),
-- 订单16: 格力空调 + 苏泊尔电饭煲
(16, 42, '格力 空调 KFR-35GW/NhBb3Bj', '/products/gree-ac-1.5.jpg', 2799.00, 1, 2799.00),
(16, 23, '苏泊尔 电饭煲 SF40HC88', '/products/supor-ricecooker.jpg', 599.00, 1, 599.00),
-- 订单17: 卫龙辣条 + 良品铺子猪肉脯
(17, 46, '卫龙 辣条大面筋 650g 怀旧装', '/products/weilao-gluten.jpg', 19.90, 5, 99.50),
(17, 37, '良品铺子 猪肉脯 200g', '/products/bestore-porkjerky.jpg', 29.90, 2, 59.80),
-- 订单18: 科沃斯扫地机器人
(18, 23, '科沃斯 T20 Pro 扫地机器人', '/products/ecovacs-t20pro.jpg', 3999.00, 1, 3999.00),
-- 订单19: Coach托特包
(19, 57, 'Coach 女士托特包 City33', '/products/coach-tote.jpg', 2950.00, 1, 2950.00),
-- 订单20: 惠普打印机
(20, 32, '惠普 LaserJet Pro M404dw', '/products/hp-m404dw.jpg', 1899.00, 1, 1899.00),
-- 订单21: ThinkPad X1 Carbon
(21, 10, '联想 ThinkPad X1 Carbon Gen11', '/products/thinkpad-x1c.jpg', 10999.00, 1, 10999.00),
-- 订单22: 兰蔻小黑瓶 (退款)
(22, 34, '兰蔻 小黑瓶精华肌底液 100ml', '/products/lancome-serum.jpg', 1080.00, 1, 1080.00),
-- 订单23: 戴森吹风机
(23, 26, '戴森 Supersonic HD15 吹风机', '/products/dyson-hd15.jpg', 2990.00, 1, 2990.00),
-- 订单24: 苹果Mac Mini
(24, 29, '苹果 Mac Mini M2', '/products/mac-mini-m2.jpg', 4499.00, 1, 4499.00),
-- 订单25: 戴尔XPS 13 Plus (待收货)
(25, 13, '戴尔 XPS 13 Plus', '/products/dell-xps13plus.jpg', 8999.00, 1, 8999.00),
-- 订单26: iPad Pro (待付款)
(26, 8, 'iPad Pro 12.9英寸 M2', '/products/ipad-pro-129.jpg', 9999.00, 1, 9999.00),
-- 订单27: 优衣库针织衫 (已取消)
(27, 43, '优衣库 女装 圆领针织衫', '/products/uniqlo-knit.jpg', 599.00, 1, 599.00),
-- 订单28: 安踏跑鞋 (退款中)
(28, 61, '安踏 跑步鞋 马赫4代', '/products/anta-running.jpg', 399.00, 1, 399.00),
-- 订单29: 九阳豆浆机
(29, 22, '九阳 破壁豆浆机 DJ10R-K16G', '/products/joyoung-soymilk.jpg', 399.00, 1, 399.00),
-- 订单30: SK-II神仙水
(30, 33, 'SK-II 神仙水 230ml', '/products/skii-facial.jpg', 1370.00, 1, 1370.00),
-- 订单31: 戴森V15 (待收货)
(31, 22, '戴森 V15 Detect 吸尘器', '/products/dyson-v15.jpg', 4990.00, 1, 4990.00),
-- 订单32: 安克充电器
(32, 14, '安克 65W氮化镓充电器', '/products/anker-65w.jpg', 128.00, 1, 128.00),
-- 订单33: 波司登羽绒服
(33, 44, '波司登 女士羽绒服 B20142322', '/products/bosideng-down-w.jpg', 899.00, 1, 899.00),
-- 订单34: 外交官行李箱 (待收货)
(34, 58, '外交官 行李箱 TEC-9系列 24寸', '/products/diplomat-luggage.jpg', 699.00, 1, 699.00),
-- 订单35: 大益普洱茶
(35, 53, '大益 普洱茶 7572熟茶饼 357g', '/products/dayi-puer.jpg', 268.00, 1, 268.00),
-- 订单36: 三只松鼠每日坚果
(36, 31, '三只松鼠 每日坚果 750g/30包', '/products/3squirrels-nuts.jpg', 89.00, 1, 89.00),
-- 订单37: 波司登羽绒服
(37, 44, '波司登 女士羽绒服 B20142322', '/products/bosideng-down-w.jpg', 899.00, 1, 899.00),
-- 订单38: 美的空气炸锅
(38, 21, '美的 空气炸锅 MF-KZE5089', '/products/midea-airfryer.jpg', 299.00, 1, 299.00),
-- 订单39: 安踏跑鞋 (待收货)
(39, 61, '安踏 跑步鞋 马赫4代', '/products/anta-running.jpg', 399.00, 1, 399.00),
-- 订单40: 花西子散粉
(40, 50, '花西子 散粉 玉容睡莲版', '/products/florasis-powder.jpg', 199.00, 1, 199.00),
-- 订单41: 李宁卫衣 (待付款)
(41, 55, '李宁 男士运动卫衣', '/products/lining-hoodie.jpg', 199.00, 1, 199.00),
-- 订单42: 阿胶糕
(42, 70, '同仁堂 阿胶糕 即食型 300g', '/products/trt-ejiao.jpg', 168.00, 1, 168.00),
-- 订单43: 挂耳咖啡
(43, 54, '隅田川 挂耳咖啡 意式风味 24片', '/products/tasogare-coffee.jpg', 49.90, 1, 49.90),
-- 订单44: 小米手环8 Pro
(44, 19, '小米手环8 Pro', '/products/xiaomi-band8pro.jpg', 349.00, 1, 349.00),
-- 订单45: 迪卡侬哑铃 (待发货)
(45, 67, '迪卡侬 可调节哑铃 20kg/只', '/products/decathlon-dumbbell.jpg', 499.00, 1, 499.00),
-- 订单46: 西湖龙井
(46, 53, '西湖牌 明前特级龙井 250g', '/products/westlake-longjing.jpg', 288.00, 1, 288.00),
-- 订单47: 百丽高跟鞋
(47, 59, '百丽 女士羊皮高跟鞋', '/products/belle-heels.jpg', 699.00, 1, 699.00),
-- 订单48: 蛋白粉 (待发货)
(48, 69, '汤臣倍健 蛋白粉 450g', '/products/by-health-protein.jpg', 198.00, 1, 198.00),
-- 订单49: 乐高火车 (待付款)
(48, 71, '乐高 城市系列 60367 客运火车', '/products/lego-train.jpg', 899.00, 1, 899.00),
-- 订单47: 闪迪存储卡
(47, 16, '闪迪 256GB TF存储卡', '/products/sandisk-256gb.jpg', 109.00, 1, 109.00);

-- ----------------------------
-- 商品评价数据 (20条评价)
-- ----------------------------
INSERT INTO `dt_product_review` (`order_id`, `product_id`, `user_id`, `rating`, `content`, `images`, `is_anonymous`) VALUES
(1, 1, 1, 5, '华为Mate60 Pro确实好用，卫星通话功能很实用，信号稳定，拍照效果也很棒，支持国货！', '["/reviews/review1-1.jpg","/reviews/review1-2.jpg"]', 0),
(2, 22, 1, 4, '戴森V15吸力很强，激光探测功能很实用，能看到平时忽略的灰尘。就是噪音有点大。', '["/reviews/review2-1.jpg"]', 0),
(3, 5, 2, 5, 'vivo X100 Pro的蔡司镜头拍照太美了！人像模式虚化自然，夜景也很清晰。电池续航给力，一天一充够用。', '["/reviews/review3-1.jpg","/reviews/review3-2.jpg","/reviews/review3-3.jpg"]', 0),
(3, 33, 2, 5, 'SK-II神仙水用了皮肤真的变细腻了，吸收快不黏腻，无限回购！', NULL, 0),
(4, 34, 2, 4, '兰蔻小黑瓶质地很好吸收，用了一段时间感觉皮肤稳定了不少。就是价格有点贵。', '["/reviews/review4-1.jpg"]', 0),
(5, 11, 3, 5, 'MacBook Pro M3 Pro性能太强了，剪辑4K视频毫无压力，续航也很出色，轻薄本首选！', '["/reviews/review5-1.jpg"]', 0),
(6, 3, 4, 4, '小米14 Ultra的徕卡镜头拍照确实有味道，色彩调校很舒服。就是系统广告有点多。', '["/reviews/review6-1.jpg","/reviews/review6-2.jpg"]', 0),
(8, 17, 5, 5, '华为MatePad Pro 13.2的屏幕太大了，看文献和做笔记很舒服，星闪笔延迟几乎为零！', NULL, 0),
(9, 41, 6, 4, '海尔501L冰箱容量够大，十字对开门分类存储很方便。风冷无霜省心，运行声音也很小。', '["/reviews/review9-1.jpg"]', 0),
(10, 2, 7, 5, 'iPhone 15 Pro Max的钛金属手感真好，轻了不少。A17 Pro性能爆表，5倍长焦拍照利器！', '["/reviews/review10-1.jpg","/reviews/review10-2.jpg"]', 0),
(12, 17, 8, 4, '华为Watch GT4外观精致，屏幕显示清晰，心率检测准确，续航14天不是吹的。', NULL, 0),
(14, 21, 9, 5, '美的空气炸锅太好用了，5L容量够一家人用，可视窗口随时查看食物状态，做出来的鸡翅外酥里嫩！', '["/reviews/review14-1.jpg"]', 0),
(16, 42, 10, 4, '格力空调制冷很快，1.5匹用在15平房间刚好。一级能效省电，WiFi控制方便。安装师傅也很专业。', NULL, 0),
(18, 23, 11, 5, '科沃斯T20 Pro太省心了，热水洗拖布功能很实用，自动集尘不用经常清理，吸力强劲。', '["/reviews/review18-1.jpg","/reviews/review18-2.jpg"]', 0),
(19, 57, 12, 4, 'Coach托特包容量很大，十字纹牛皮质感好，上班通勤够用。就是自重稍微有点重。', '["/reviews/review19-1.jpg"]', 0),
(23, 26, 14, 5, '戴森吹风机干发速度确实快，智能温控不伤发，用完头发顺滑很多。除了贵没毛病！', NULL, 0),
(33, 44, 16, 4, '波司登羽绒服轻薄又保暖，90%白鹅绒不是盖的，-10度穿一件毛衣+这件就够了。', '["/reviews/review33-1.jpg"]', 0),
(37, 44, 18, 5, '给妈妈买的波司登，她很喜欢，说比之前那件轻多了但更暖和。', NULL, 1),
(44, 19, 10, 4, '小米手环8 Pro屏幕大了很多，消息提醒方便，GPS定位准确。性价比很高的穿戴设备。', NULL, 0),
(47, 16, 20, 5, '闪迪256GB存储卡用在行车记录仪上很好，连续写入稳定，宽温设计夏天不怕高温。', NULL, 0);

-- ============================================================
-- 数据统计概览 (仅供验证，可删除)
-- ============================================================
SELECT '用户总数' AS `统计项`, COUNT(*) AS `数量` FROM dt_user
UNION ALL
SELECT '地址总数', COUNT(*) FROM dt_user_address
UNION ALL
SELECT '分类总数', COUNT(*) FROM dt_category
UNION ALL
SELECT '商品总数', COUNT(*) FROM dt_product
UNION ALL
SELECT '购物车项', COUNT(*) FROM dt_cart_item
UNION ALL
SELECT '订单总数', COUNT(*) FROM dt_order
UNION ALL
SELECT '订单明细', COUNT(*) FROM dt_order_item
UNION ALL
SELECT '评价总数', COUNT(*) FROM dt_product_review
UNION ALL
SELECT '优惠券数', COUNT(*) FROM dt_coupon
UNION ALL
SELECT '用户领券', COUNT(*) FROM dt_user_coupon
UNION ALL
SELECT '商品总库存', SUM(stock) FROM dt_product
UNION ALL
SELECT '商品总销量', SUM(sales) FROM dt_product;
