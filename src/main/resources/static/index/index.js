const MAX_DATA_POINTS = 60; // 存储60分钟（1小时）的数据

// 模拟实时数据
let timeLabels = [];
let tempData = [];
let humidityData = [];

// 初始化图表
const ctx = document.getElementById('tempHumidityChart').getContext('2d');
const chart = new Chart(ctx, {
    type: 'line',
    data: {
        labels: timeLabels,
        datasets: [
            {
                label: '温度 (℃)',
                data: tempData,
                borderColor: 'rgb(255, 99, 132)',
                backgroundColor: 'rgba(255, 99, 132, 0.2)',
                yAxisID: 'yTemp',
                fill: true,
                tension: 0.1 // 使曲线更平滑
            },
            {
                label: '湿度 (%)',
                data: humidityData,
                borderColor: 'rgb(54, 162, 235)',
                backgroundColor: 'rgba(54, 162, 235, 0.2)',
                yAxisID: 'yHumid',
                fill: true,
                tension: 0.1
            }
        ]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false, // 允许图表自由伸缩
        scales: {
            yTemp: {
                type: 'linear',
                display: true,
                position: 'left',
                title: { display: true, text: '温度 (℃)' },
                suggestedMin: 20, suggestedMax: 35
            },
            yHumid: {
                type: 'linear',
                display: true,
                position: 'right',
                title: { display: true, text: '湿度 (%)' },
                suggestedMin: 40, suggestedMax: 80,
                grid: { drawOnChartArea: false } // 仅绘制左侧网格线
            }
        },
        plugins: {
            tooltip: {
                mode: 'index',
                intersect: false,
            }
        },
        hover: {
            mode: 'nearest',
            intersect: true
        }
    }
});

// 模拟数据生成函数
function fetchData() {
    const now = new Date();
    // 修正: 确保时间字符串格式正确（分:秒）
    const timeString = `${now.getMinutes() < 10 ? '0' : ''}${now.getMinutes()}:${now.getSeconds() < 10 ? '0' : ''}${now.getSeconds()}`;

    // 模拟新的温度、湿度和 pH 值
    const newTemp = (Math.random() * 3 + 24).toFixed(1);
    const newHumid = (Math.random() * 10 + 60).toFixed(0);
    // ***** 修正 1: 模拟 pH 值 (例如 5.5 到 7.5 之间，保留一位小数) *****
    const newPH = (Math.random() * 2 + 5.5).toFixed(1);

    // 更新图表数据
    timeLabels.push(timeString);
    tempData.push(newTemp);
    humidityData.push(newHumid);

    // 保持数据点数量不超过 MAX_DATA_POINTS
    if (timeLabels.length > MAX_DATA_POINTS) {
        timeLabels.shift();
        tempData.shift();
        humidityData.shift();
    }

    // 更新实时数值显示
    document.getElementById('currentTemp').textContent = newTemp;
    document.getElementById('currentHumid').textContent = newHumid;
    // ***** 修正 2: 更改 ID 'currentLight' 为 'currentPh' *****
    document.getElementById('currentPh').textContent = newPH;

    // 重新渲染图表
    chart.update();
}

// 每隔 5 秒获取一次数据（演示效果）
setInterval(fetchData, 5000);

// 初始化加载第一批数据
fetchData();


// --- 交互事件处理 ---

// 设置目标湿度按钮事件 (原浇水功能替换)
document.getElementById('setHumidBtn').addEventListener('click', () => {
    const target = document.getElementById('targetHumid').value;
    if (target && !isNaN(target)) {
        // 模仿升温加热的 alert 格式
        alert(`💦 提高湿度指令已发送！目标湿度设定为 ${target}%。`);
        console.log(`发送提高湿度指令，目标 ${target}%...`);
    } else {
        alert('请输入有效的目标湿度！');
    }
});
// 升温按钮事件
document.getElementById('heatBtn').addEventListener('click', () => {
    const target = document.getElementById('targetTemp').value;
    if (target && !isNaN(target)) {
        alert(`🔥 升温指令已发送！目标温度设定为 ${target}℃。`);
        console.log(`发送升温指令，目标 ${target}℃...`);
    } else {
        alert('请输入有效的目标温度！');
    }
});


// --- 天气预报功能 ---

// ⚠️ 请将 'YOUR_API_KEY' 替换为您自己的 OpenWeatherMap API Key
const API_KEY = '07f1b15756b74cfdb9c135254252511';
const DEFAULT_CITY = '成都';


async function fetchWeather(city) {
    const weatherInfoDiv = document.getElementById('weatherInfo');

    if (!API_KEY || API_KEY === "YOUR_API_KEY") {
        weatherInfoDiv.innerHTML = '<p style="color:red;">⚠️ 请先填写 WeatherAPI Key！</p>';
        return;
    }

    const apiUrl = `https://api.weatherapi.com/v1/current.json?key=${API_KEY}&q=${city}&lang=zh`;

    try {
        weatherInfoDiv.innerHTML = `<p>正在查询 ${city} 的天气...</p>`;

        const response = await fetch(apiUrl);
        if (!response.ok) {
            throw new Error(`无法获取 ${city} 的天气，请检查城市名称`);
        }

        const data = await response.json();
        displayWeather(data);

    } catch (error) {
        weatherInfoDiv.innerHTML = `<p style="color:red;">错误：${error.message}</p>`;
        console.error("WeatherAPI 获取失败：", error);
    }
}

// 显示 WeatherAPI 天气数据
function displayWeather(data) {
    const weatherInfoDiv = document.getElementById('weatherInfo');

    const temp = data.current.temp_c;
    const description = data.current.condition.text;
    const iconUrl = "https:" + data.current.condition.icon; // WeatherAPI 返回 //cdn… 需要补 https:

    weatherInfoDiv.innerHTML = `
        <p>
            <img src="${iconUrl}" class="weather-icon">
            <strong>${data.location.name}</strong>（实时）
        </p>
        <p>🌡️ 温度：<strong>${temp} ℃</strong></p>
        <p>☁️ 描述：${description}</p>
        <p>💧 湿度：${data.current.humidity} %</p>
        <p>💨 风速：${data.current.wind_kph} km/h</p>
    `;
}

// 默认加载
document.getElementById('cityInput').value = DEFAULT_CITY;
fetchWeather(DEFAULT_CITY);

// 查询按钮
document.getElementById('getWeatherBtn').addEventListener('click', () => {
    const city = document.getElementById('cityInput').value.trim();
    if (city) fetchWeather(city);
});


// --- 新增: 接收 AI 指令并自动执行升温操作 ---
// 此逻辑在所有 DOM 和 Chart 初始化完成后执行

// --- 新增/修改: 接收 AI 指令并自动执行温湿度操作 ---
// 此逻辑在所有 DOM 和 Chart 初始化完成后执行

function checkAICommand() {
    // 获取 chat.html 中存储的值和标志
    const aiSetTemp = localStorage.getItem("aiSetTemp");
    const aiAutoHeat = localStorage.getItem("aiAutoHeat");
    const aiSetHumid = localStorage.getItem("aiSetHumid"); // 新增：目标湿度值
    const aiAutoHumid = localStorage.getItem("aiAutoHumid"); // 新增：湿度指令标志

    let tempExecuted = false; // 标记温度指令是否执行

    // 1. **执行温度指令 (优先)**
    if (aiSetTemp && aiAutoHeat === "true" && !isNaN(parseFloat(aiSetTemp))) {
        const targetTempInput = document.getElementById('targetTemp');

        // 确保值有效
        if (!isNaN(parseFloat(aiSetTemp))) {
            targetTempInput.value = parseFloat(aiSetTemp);
        } else {
            console.error("AI 设定的温度值无效:", aiSetTemp);
            localStorage.removeItem("aiSetTemp");
            localStorage.removeItem("aiAutoHeat");
            return;
        }

        setTimeout(() => {
            document.getElementById('heatBtn').click();
            localStorage.removeItem("aiSetTemp");
            localStorage.removeItem("aiAutoHeat");
            console.log(`AI助手指令(1/2)已执行：目标温度设置为 ${aiSetTemp}℃ 并发送升温指令。`);
        }, 500); // 延迟 0.5 秒执行温度指令
        tempExecuted = true;
    } else {
        // 清理无效或未执行的温度指令
        localStorage.removeItem("aiSetTemp");
        localStorage.removeItem("aiAutoHeat");
    }

    // 2. **执行湿度指令 (在温度指令后)**
    if (aiSetHumid && aiAutoHumid === "true" && !isNaN(parseFloat(aiSetHumid))) {
        const targetHumidInput = document.getElementById('targetHumid');
        targetHumidInput.value = parseFloat(aiSetHumid);

        // 如果执行了温度指令，则延迟更久（1.5 秒），否则延迟 0.5 秒
        const delay = tempExecuted ? 1500 : 500;

        setTimeout(() => {
            document.getElementById('setHumidBtn').click();
            localStorage.removeItem("aiSetHumid");
            localStorage.removeItem("aiAutoHumid");
            console.log(`AI助手指令(${tempExecuted ? '2/2' : '1/1'})已执行：目标湿度设置为 ${aiSetHumid}% 并发送提高湿度指令。`);
        }, delay);
    } else {
        // 清理无效或未执行的湿度指令
        localStorage.removeItem("aiSetHumid");
        localStorage.removeItem("aiAutoHumid");
    }
}
// 页面加载完成后立即检查 AI 指令
checkAICommand();